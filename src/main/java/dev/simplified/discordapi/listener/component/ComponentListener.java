package dev.simplified.discordapi.listener.component;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.component.Component;
import dev.simplified.discordapi.component.capability.EventInteractable;
import dev.simplified.discordapi.component.capability.UserInteractable;
import dev.simplified.discordapi.context.EventContext;
import dev.simplified.discordapi.context.capability.ExceptionContext;
import dev.simplified.discordapi.context.scope.ComponentContext;
import dev.simplified.discordapi.handler.ComponentDispatcher;
import dev.simplified.discordapi.handler.ComponentRouteTtlContextKey;
import dev.simplified.discordapi.handler.response.CachedResponse;
import dev.simplified.discordapi.handler.response.ResponseLocator;
import dev.simplified.discordapi.listener.DiscordListener;
import dev.simplified.discordapi.response.Response;
import dev.simplified.reflection.Reflection;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Abstract base for component interaction listeners, providing the shared flow
 * of matching an incoming event to a {@link CachedResponse} via the
 * {@link ResponseLocator ResponseLocator},
 * locating the interacted {@link EventInteractable}, and dispatching to its
 * registered handler.
 *
 * <p>
 * Dispatch precedence:
 * <ul>
 *   <li><b>Cache hit + annotation route</b> - the matched
 *       {@link dev.simplified.discordapi.listener.Component @Component} handler is invoked
 *       with a context built from the cached response</li>
 *   <li><b>Cache hit + inline component</b> - the cached component's
 *       interaction lambda is invoked</li>
 *   <li><b>Cache miss + annotation route</b> - an eternal context is
 *       synthesized and the {@code @Component} handler is invoked</li>
 *   <li><b>Cache miss + no route</b> - the interaction is dropped via
 *       {@code deferEdit().then()}</li>
 * </ul>
 *
 * @param <E> the Discord4J component interaction event type
 * @param <C> the context type passed to the component's interaction handler
 * @param <T> the component type this listener handles
 */
public abstract class ComponentListener<E extends ComponentInteractionEvent, C extends ComponentContext, T extends EventInteractable<C>> extends DiscordListener<E> {

    /** The resolved component class, used to filter matching components from the response tree. */
    private final Class<T> componentClass;

    /**
     * Constructs a new {@code ComponentListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    protected ComponentListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
        this.componentClass = Reflection.getSuperClass(this, 2);
    }

    @Override
    public final Publisher<Void> apply(@NotNull E event) {
        if (event.getInteraction().getUser().isBot())
            return Mono.empty();

        return this.getDiscordBot()
            .getResponseLocator()
            .findByMessage(event.getMessageId())
            // thenReturn keeps the entry emitting so switchIfEmpty fires ONLY on a genuine cache miss -
            // handleEvent returns Mono<Void> (emits nothing), which would otherwise always trip the eternal
            // fallback and double-acknowledge the interaction.
            .flatMap(entry -> this.handleEvent(event, entry).thenReturn(entry))
            .switchIfEmpty(Mono.defer(() -> this.tryDispatchEternal(event).then(Mono.empty())))
            .then()
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Routes a matched cache entry to the appropriate dispatch path. Override
     * for special handling (e.g. modals) that needs to bypass the standard
     * tree walk. The annotation dispatcher is consulted first; if no route
     * matches, the inline path is used as a fallback.
     */
    protected Mono<Void> handleEvent(@NotNull E event, @NotNull CachedResponse entry) {
        RouteResolution resolution = this.resolveRoute(event);

        return switch (resolution.kind()) {
            case DISPATCH -> this.dispatchAnnotation(event, entry, resolution.route().orElseThrow());
            case DROP -> this.dropInteraction(event);
            case MISS -> this.dispatchInline(event, entry);
        };
    }

    /**
     * Resolves the annotation route for the event, reporting whether it should
     * be dispatched, dropped (ambiguous or context-type mismatch), or is absent
     * (so callers may fall back to inline dispatch).
     */
    private @NotNull RouteResolution resolveRoute(@NotNull E event) {
        Optional<ComponentDispatcher.MatchedRoute> matched = this.getDiscordBot()
            .getComponentDispatcher()
            .findRoute(event.getCustomId());

        if (matched.isEmpty())
            return RouteResolution.miss();

        ComponentDispatcher.MatchedRoute route = matched.get();
        if (route.getKind() == ComponentDispatcher.MatchedRoute.Kind.AMBIGUOUS)
            return RouteResolution.drop();

        ComponentDispatcher.ComponentRoute componentRoute = route.getRoute().orElseThrow();
        Class<?> expected = componentRoute.getExpectedContextType();
        if (!this.expectedContextMatches(expected)) {
            this.getLog().warn(
                "@Component route '{}' on {} expected {} but listener {} dispatches a different context type",
                event.getCustomId(),
                componentRoute.getOwnerClass().getName(),
                expected.getSimpleName(),
                this.getClass().getSimpleName()
            );
            return RouteResolution.drop();
        }

        return RouteResolution.dispatch(componentRoute);
    }

    /**
     * Inline dispatch path: walks the response's current component tree to find
     * a matching {@link UserInteractable} by custom id and invokes its inline
     * interaction lambda.
     */
    private @NotNull Mono<Void> dispatchInline(@NotNull E event, @NotNull CachedResponse entry) {
        return Mono.justOrEmpty(this.matchComponent(event, entry.getResponse()))
            .flatMap(component -> this.handleInteraction(event, entry, component, followupOf(entry)))
            .then();
    }

    /**
     * Annotation dispatch path: invokes the {@link ComponentDispatcher} route
     * via its {@link java.lang.invoke.MethodHandle MethodHandle}, editing the
     * response if the handler modified it and decorating the pipeline with the
     * per-route cache TTL context key when the route declares one.
     */
    private @NotNull Mono<Void> dispatchAnnotation(@NotNull E event, @NotNull CachedResponse entry, @NotNull ComponentDispatcher.ComponentRoute route) {
        Optional<CachedResponse> followup = followupOf(entry);

        // Find the matching component on the response so the inline handler
        // contract (modal handlers, deferEdit gating, etc.) still sees a
        // concrete component instance.
        Optional<T> matched = this.matchComponent(event, entry.getResponse());
        if (matched.isEmpty())
            return this.dropInteraction(event);

        entry.setBusy(); // reset acknowledgment state for this interaction (each event has its own ack budget)
        C context = this.getContext(event, entry.getResponse(), matched.get(), followup);

        Mono<Void> dispatchMono = this.invokeRoute(route, context)
            .checkpoint("ComponentListener#dispatchAnnotation Processing")
            .onErrorResume(throwable -> this.onDispatchError(event, context, throwable))
            .then(this.editIfModified(entry, context, followup));

        return this.withCacheTtl(dispatchMono, route);
    }

    /**
     * Eternal dispatch path: invoked when the response locator has no cached
     * entry for the incoming event's message. Resolves an annotation route for
     * the {@code customId}; on a dispatchable route, builds an eternal context
     * via {@link #getEternalContext(ComponentInteractionEvent)} and invokes it.
     * On a miss, ambiguous match, or context-type mismatch, the interaction is
     * dropped with {@code deferEdit}.
     */
    private @NotNull Mono<Void> tryDispatchEternal(@NotNull E event) {
        RouteResolution resolution = this.resolveRoute(event);
        if (resolution.kind() != RouteResolution.Kind.DISPATCH)
            return this.dropInteraction(event);

        ComponentDispatcher.ComponentRoute route = resolution.route().orElseThrow();
        C context = this.getEternalContext(event);

        Mono<Void> dispatchMono = this.invokeRoute(route, context)
            .checkpoint("ComponentListener#tryDispatchEternal Processing")
            .onErrorResume(throwable -> this.onDispatchError(event, context, throwable))
            .then();

        return this.withCacheTtl(dispatchMono, route);
    }

    /**
     * Executes the component's registered inline interaction handler within an
     * error-handling pipeline, then finalizes the interaction exactly once:
     * editing the response when the handler modified it, or otherwise recording
     * the interaction.
     *
     * @param event the Discord4J interaction event
     * @param entry the matched response cache entry
     * @param component the matched component
     * @param followup the matched followup, if the interaction targets one
     * @return a reactive pipeline completing when the interaction is handled
     */
    protected final @NotNull Mono<Void> handleInteraction(@NotNull E event, @NotNull CachedResponse entry, @NotNull T component, @NotNull Optional<CachedResponse> followup) {
        C context = this.getContext(event, entry.getResponse(), component, followup);
        entry.setBusy(); // reset acknowledgment state for this interaction (each event has its own ack budget)

        Mono<Void> deferEdit = context.deferEdit(); // idempotent: a no-op once the interaction is acknowledged

        return (component.isDeferEdit() ? deferEdit : Mono.<Void>empty())
            .then(Mono.defer(() -> component.getInteraction().apply(context)))
            .checkpoint("ComponentListener#handleInteraction Processing")
            .onErrorResume(throwable -> this.onDispatchError(event, context, throwable))
            .then(this.editIfModified(entry, context, followup));
    }

    // --- shared dispatch helpers ---

    /**
     * Walks the response's current component tree (both the cached pagination
     * components and the current page's own components) for a
     * {@link UserInteractable} whose identifier matches the event's custom id
     * and whose type this listener handles.
     */
    private @NotNull Optional<T> matchComponent(@NotNull E event, @NotNull Response response) {
        return response.getCurrentComponents()
            .flatMap(Component::flattenComponents)
            .filter(UserInteractable.class::isInstance)
            .filter(component -> event.getCustomId().equals(((UserInteractable) component).getIdentifier()))
            .filter(this.componentClass::isInstance)
            .map(this.componentClass::cast)
            .findFirst();
    }

    /**
     * Finalizes the interaction exactly once: editing the response through the
     * interaction (which clears the dirty flag internally) when the handler
     * modified it, or otherwise recording the interaction. Consolidating both
     * outcomes here keeps the dispatch chains from double-finalizing.
     */
    private @NotNull Mono<Void> editIfModified(@NotNull CachedResponse entry, @NotNull C context, @NotNull Optional<CachedResponse> followup) {
        return Mono.defer(() -> {
            if (entry.isModified())
                return followup.isEmpty() ? context.edit() : context.editFollowup();

            return entry.updateLastInteract().then();
        });
    }

    /** Drops an interaction that has no handler by acknowledging it with a deferred edit. */
    private @NotNull Mono<Void> dropInteraction(@NotNull E event) {
        return event.deferEdit().then();
    }

    /**
     * Acknowledges the interaction before reporting a handler error, so Discord
     * does not surface "interaction failed". Cached contexts acknowledge through
     * the guarded cache entry; eternal contexts fall back to the raw event. A
     * failing acknowledgment is swallowed so it cannot mask the original error.
     */
    private @NotNull Mono<Void> onDispatchError(@NotNull E event, @NotNull C context, @NotNull Throwable throwable) {
        return Mono.defer(() -> context.findResponseCacheEntry().isPresent() ? context.deferEdit() : event.deferEdit())
            .onErrorResume(ignored -> Mono.empty())
            .then(this.reportException(context, throwable));
    }

    /** Forwards a handler error to the bot's exception handler with this listener's title. */
    private @NotNull Mono<Void> reportException(@NotNull C context, @NotNull Throwable throwable) {
        return this.getDiscordBot().getExceptionHandler().handleException(
            ExceptionContext.of(
                this.getDiscordBot(),
                context,
                throwable,
                String.format("%s Exception", this.getTitle())
            )
        );
    }

    /** Invokes an annotation route's method handle and adapts its publisher to a {@code Mono<Void>}. */
    @SuppressWarnings("unchecked")
    private @NotNull Mono<Void> invokeRoute(@NotNull ComponentDispatcher.ComponentRoute route, @NotNull C context) {
        return Mono.from((Publisher<Void>) tryInvoke(route, context));
    }

    /** Decorates a dispatch pipeline with the route's cache time-to-live context key when it declares one. */
    private @NotNull Mono<Void> withCacheTtl(@NotNull Mono<Void> dispatch, @NotNull ComponentDispatcher.ComponentRoute route) {
        if (route.getCacheTtl() <= 0)
            return dispatch;

        Duration ttl = Duration.ofSeconds(route.getCacheTtl());
        return dispatch.contextWrite(reactorCtx -> reactorCtx.put(ComponentRouteTtlContextKey.KEY, ttl));
    }

    /** Wraps an entry as a followup when it represents one, so followup edits target the correct message. */
    private static @NotNull Optional<CachedResponse> followupOf(@NotNull CachedResponse entry) {
        return entry.isFollowup() ? Optional.of(entry) : Optional.empty();
    }

    /**
     * Returns whether the given expected context type from a registered
     * annotation route matches what this listener dispatches.
     */
    private boolean expectedContextMatches(@NotNull Class<?> expected) {
        return expected.isAssignableFrom(this.getContextClass())
            || this.getContextClass().isAssignableFrom(expected);
    }

    /** The static context class this listener constructs and dispatches. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private @NotNull Class<C> getContextClass() {
        return (Class<C>) (Class) Reflection.getSuperClass(this, 1);
    }

    /** Reflection helper that throws checked exceptions through {@link RuntimeException}. */
    private static Object tryInvoke(@NotNull ComponentDispatcher.ComponentRoute route, @NotNull Object context) {
        try {
            return route.getMethodHandle().invoke(route.getInstance(), context);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Computes the deterministic eternal {@link UUID} for the given event's
     * message snowflake. The same message always yields the same UUID, so a
     * synthesized context for an eternal interaction is stable across
     * dispatches.
     *
     * @param event the component interaction event
     * @return the deterministic eternal response id
     */
    protected static @NotNull UUID computeEternalResponseId(@NotNull ComponentInteractionEvent event) {
        return UUID.nameUUIDFromBytes(("eternal:" + event.getMessageId().asLong()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates the typed context for a cached component interaction.
     *
     * @param event the Discord4J interaction event
     * @param cachedMessage the cached response containing the component
     * @param component the matched component
     * @param followup the matched followup, if the interaction targets one
     * @return the constructed context
     */
    protected abstract @NotNull C getContext(@NotNull E event, @NotNull Response cachedMessage, @NotNull T component, @NotNull Optional<CachedResponse> followup);

    /**
     * Creates the typed context for an annotation-dispatched eternal
     * interaction whose backing message has no cache entry. Subclasses
     * synthesize a minimal component carrying the {@code customId} (and any
     * Discord-side input values) and return a context whose
     * {@link EventContext#getResponseId() responseId}
     * is the deterministic id from
     * {@link #computeEternalResponseId(ComponentInteractionEvent)}.
     *
     * @param event the Discord4J interaction event
     * @return the constructed eternal context
     */
    protected abstract @NotNull C getEternalContext(@NotNull E event);

    /**
     * Outcome of resolving an annotation route for an incoming interaction:
     * {@link Kind#DISPATCH} carries the route to invoke, {@link Kind#DROP}
     * signals an ambiguous or context-type-mismatched route the caller must
     * drop, and {@link Kind#MISS} signals no route matched (callers may fall
     * back to inline dispatch).
     */
    private record RouteResolution(@NotNull Kind kind, @NotNull Optional<ComponentDispatcher.ComponentRoute> route) {

        /** Discriminator for how a resolved route should be handled. */
        private enum Kind {

            /** A single route matched and should be invoked. */
            DISPATCH,

            /** A route matched but cannot be dispatched; the interaction must be dropped. */
            DROP,

            /** No route matched; the caller may fall back to inline dispatch. */
            MISS

        }

        private static @NotNull RouteResolution dispatch(@NotNull ComponentDispatcher.ComponentRoute route) {
            return new RouteResolution(Kind.DISPATCH, Optional.of(route));
        }

        private static @NotNull RouteResolution drop() {
            return new RouteResolution(Kind.DROP, Optional.empty());
        }

        private static @NotNull RouteResolution miss() {
            return new RouteResolution(Kind.MISS, Optional.empty());
        }

    }

}
