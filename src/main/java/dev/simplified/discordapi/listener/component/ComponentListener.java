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
import reactor.core.publisher.Flux;
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
        Optional<ComponentDispatcher.MatchedRoute> matched = this.getDiscordBot()
            .getComponentDispatcher()
            .findRoute(event.getCustomId());

        if (matched.isPresent()) {
            ComponentDispatcher.MatchedRoute route = matched.get();
            if (route.getKind() == ComponentDispatcher.MatchedRoute.Kind.AMBIGUOUS)
                return event.deferEdit().then();

            return this.dispatchAnnotation(event, entry, route.getRoute().orElseThrow());
        }

        return this.dispatchInline(event, entry);
    }

    /**
     * Inline dispatch path: walks the response's component tree to find a
     * matching {@link UserInteractable} by custom id and invokes its inline
     * interaction lambda.
     */
    private @NotNull Mono<Void> dispatchInline(@NotNull E event, @NotNull CachedResponse entry) {
        entry.setBusy();
        Optional<CachedResponse> followup = entry.isFollowup() ? Optional.of(entry) : Optional.empty();
        CachedResponse target = followup.orElse(entry);

        return Flux.fromIterable(target.getResponse().getCachedPageComponents())
            .concatWith(Flux.fromIterable(target.getResponse().getHistoryHandler().getCurrentPage().getComponents()))
            .concatMap(tlmComponent -> Flux.fromStream(tlmComponent.flattenComponents()))
            .filter(UserInteractable.class::isInstance)
            .filter(component -> event.getCustomId().equals(((UserInteractable) component).getIdentifier()))
            .filter(this.componentClass::isInstance)
            .map(this.componentClass::cast)
            .singleOrEmpty()
            .flatMap(component -> this.handleInteraction(event, entry, component, followup))
            .then(entry.updateLastInteract())
            .then();
    }

    /**
     * Annotation dispatch path: invokes the {@link ComponentDispatcher} route
     * via its {@link java.lang.invoke.MethodHandle MethodHandle} and decorates
     * the resulting publisher with the per-route cache TTL context key when
     * the route declares one.
     */
    @SuppressWarnings("unchecked")
    private @NotNull Mono<Void> dispatchAnnotation(@NotNull E event, @NotNull CachedResponse entry, @NotNull ComponentDispatcher.ComponentRoute route) {
        entry.setBusy();
        Optional<CachedResponse> followup = entry.isFollowup() ? Optional.of(entry) : Optional.empty();
        CachedResponse target = followup.orElse(entry);

        Class<?> expected = route.getExpectedContextType();
        if (!this.expectedContextMatches(expected)) {
            this.getLog().warn(
                "@Component route '{}' on {} expected {} but listener {} dispatches a different context type",
                event.getCustomId(),
                route.getOwnerClass().getName(),
                expected.getSimpleName(),
                this.getClass().getSimpleName()
            );
            return event.deferEdit().then();
        }

        // Find the matching component on the response so the inline handler
        // contract (modal handlers, deferEdit gating, etc.) still sees a
        // concrete component instance.
        Optional<T> matched = target.getResponse()
            .getCachedPageComponents()
            .stream()
            .flatMap(Component::flattenComponents)
            .filter(UserInteractable.class::isInstance)
            .filter(component -> event.getCustomId().equals(((UserInteractable) component).getIdentifier()))
            .filter(this.componentClass::isInstance)
            .map(this.componentClass::cast)
            .findFirst();

        if (matched.isEmpty())
            return event.deferEdit().then();

        C context = this.getContext(event, target.getResponse(), matched.get(), followup);

        Mono<Void> dispatchMono = Mono.from((Publisher<Void>) tryInvoke(route, context))
            .checkpoint("ComponentListener#dispatchAnnotation Processing")
            .onErrorResume(throwable -> this.getDiscordBot().getExceptionHandler().handleException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    context,
                    throwable,
                    String.format("%s Exception", this.getTitle())
                )
            ))
            .then(Mono.defer(() -> entry.isModified()
                ? (followup.isEmpty() ? context.edit() : context.editFollowup())
                : Mono.empty()
            ))
            .then(entry.updateLastInteract())
            .then();

        if (route.getCacheTtl() > 0) {
            Duration ttl = Duration.ofSeconds(route.getCacheTtl());
            dispatchMono = dispatchMono.contextWrite(reactorCtx -> reactorCtx.put(ComponentRouteTtlContextKey.KEY, ttl));
        }

        return dispatchMono;
    }

    /**
     * Eternal dispatch path: invoked when the response locator has no cached
     * entry for the incoming event's message. Looks up an annotation route
     * for the {@code customId}; on hit, builds an eternal context via
     * {@link #getEternalContext(ComponentInteractionEvent)} and invokes the
     * route. On miss or ambiguous match, drops the interaction with
     * {@code deferEdit}.
     */
    @SuppressWarnings("unchecked")
    private @NotNull Mono<Void> tryDispatchEternal(@NotNull E event) {
        Optional<ComponentDispatcher.MatchedRoute> matched = this.getDiscordBot()
            .getComponentDispatcher()
            .findRoute(event.getCustomId());

        if (matched.isEmpty())
            return event.deferEdit().then();

        ComponentDispatcher.MatchedRoute route = matched.get();
        if (route.getKind() == ComponentDispatcher.MatchedRoute.Kind.AMBIGUOUS)
            return event.deferEdit().then();

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
            return event.deferEdit().then();
        }

        C context = this.getEternalContext(event);

        Mono<Void> dispatchMono = Mono.from((Publisher<Void>) tryInvoke(componentRoute, context))
            .checkpoint("ComponentListener#tryDispatchEternal Processing")
            .onErrorResume(throwable -> this.getDiscordBot().getExceptionHandler().handleException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    context,
                    throwable,
                    String.format("%s Exception", this.getTitle())
                )
            ))
            .then();

        if (componentRoute.getCacheTtl() > 0) {
            Duration ttl = Duration.ofSeconds(componentRoute.getCacheTtl());
            dispatchMono = dispatchMono.contextWrite(reactorCtx -> reactorCtx.put(ComponentRouteTtlContextKey.KEY, ttl));
        }

        return dispatchMono;
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
     * Executes the component's registered inline interaction handler within an
     * error-handling pipeline, then edits the response if it was modified.
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
            .onErrorResume(throwable -> deferEdit.then(
                this.getDiscordBot().getExceptionHandler().handleException(
                    ExceptionContext.of(
                        this.getDiscordBot(),
                        context,
                        throwable,
                        String.format("%s Exception", this.getTitle())
                    )
                )
            ))
            .then(Mono.defer(() -> entry.isModified()
                ? (followup.isEmpty() ? context.edit() : context.editFollowup())
                : Mono.empty()
            ));
    }

}
