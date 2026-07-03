package dev.simplified.discordapi.listener.component;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.component.Component;
import dev.simplified.discordapi.component.capability.EventInteractable;
import dev.simplified.discordapi.component.capability.UserInteractable;
import dev.simplified.discordapi.component.interaction.Button;
import dev.simplified.discordapi.component.interaction.Modal;
import dev.simplified.discordapi.component.interaction.SelectMenu;
import dev.simplified.discordapi.context.capability.ExceptionContext;
import dev.simplified.discordapi.context.component.ButtonContext;
import dev.simplified.discordapi.context.component.ModalContext;
import dev.simplified.discordapi.context.component.SelectMenuContext;
import dev.simplified.discordapi.context.scope.ComponentContext;
import dev.simplified.discordapi.handler.ComponentDispatcher;
import dev.simplified.discordapi.handler.ComponentRouteTtlContextKey;
import dev.simplified.discordapi.handler.response.CachedResponse;
import dev.simplified.discordapi.handler.response.ResponseLocator;
import dev.simplified.discordapi.listener.DiscordListener;
import dev.simplified.discordapi.response.Response;
import dev.simplified.reflection.Reflection;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Single listener for all component interactions (buttons, select menus, and modal submits),
 * matching an incoming event to a {@link CachedResponse} via the
 * {@link ResponseLocator ResponseLocator} and dispatching it to the interacted component's
 * registered handler.
 *
 * <p>
 * Because every component kind builds its own typed context via
 * {@link EventInteractable#createContext}, one listener dispatches them all polymorphically. Modal
 * submits branch to the active-modal flow; button and select-menu clicks branch to route/inline
 * dispatch.
 *
 * <p>
 * Dispatch precedence for button/select clicks:
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
 */
public final class ComponentListener extends DiscordListener<ComponentInteractionEvent> {

    /**
     * Constructs a new {@code ComponentListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public ComponentListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public @NotNull Publisher<Void> apply(@NotNull ComponentInteractionEvent event) {
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

    /** Branches a cached interaction to the modal-submit flow or the button/select click flow. */
    private @NotNull Mono<Void> handleEvent(@NotNull ComponentInteractionEvent event, @NotNull CachedResponse entry) {
        if (event instanceof ModalSubmitInteractionEvent modalEvent)
            return this.handleModalSubmit(modalEvent, entry);

        return this.handleComponentClick(event, entry);
    }

    /**
     * Handles a button or select-menu click on a cached message: resolves the interacted component
     * and dispatches it via its annotation route (if any) or its inline handler.
     */
    private @NotNull Mono<Void> handleComponentClick(@NotNull ComponentInteractionEvent event, @NotNull CachedResponse entry) {
        Optional<EventInteractable<?>> matched = this.matchComponent(event, entry.getResponse());
        if (matched.isEmpty())
            return this.dropInteraction(event);

        EventInteractable<?> component = matched.get();
        Optional<CachedResponse> followup = followupOf(entry);
        RouteResolution resolution = this.resolveRoute(event);

        return switch (resolution.kind()) {
            case DISPATCH -> this.dispatchAnnotation(component, event, entry, resolution.route().orElseThrow(), followup);
            case DROP -> this.dropInteraction(event);
            case MISS -> this.dispatchInline(component, event, entry, followup);
        };
    }

    /**
     * Handles a modal submit by matching it against the user's active modal stored on the cached
     * entry and dispatching to the modal's registered handler.
     */
    private @NotNull Mono<Void> handleModalSubmit(@NotNull ModalSubmitInteractionEvent event, @NotNull CachedResponse entry) {
        Optional<CachedResponse> followup = followupOf(entry);

        return Mono.justOrEmpty(entry.getUserModal(event.getInteraction().getUser()))
            .filter(modal -> event.getCustomId().equals(modal.getIdentifier()))
            .doOnNext(modal -> entry.clearModal(event.getInteraction().getUser()))
            .flatMap(modal -> this.dispatchInline(modal, event, entry, followup))
            .then();
    }

    /**
     * Resolves the annotation route for the event, reporting whether it should be dispatched,
     * dropped (ambiguous match), or is absent (so callers may fall back to inline dispatch).
     */
    private @NotNull RouteResolution resolveRoute(@NotNull ComponentInteractionEvent event) {
        Optional<ComponentDispatcher.MatchedRoute> matched = this.getDiscordBot()
            .getComponentDispatcher()
            .findRoute(event.getCustomId());

        if (matched.isEmpty())
            return RouteResolution.miss();

        ComponentDispatcher.MatchedRoute route = matched.get();
        if (route.getKind() == ComponentDispatcher.MatchedRoute.Kind.AMBIGUOUS)
            return RouteResolution.drop();

        return RouteResolution.dispatch(route.getRoute().orElseThrow());
    }

    /**
     * Inline dispatch: builds the component's context, runs its inline interaction handler within an
     * error-handling pipeline, then finalizes the interaction exactly once.
     */
    private <X extends ComponentContext> @NotNull Mono<Void> dispatchInline(@NotNull EventInteractable<X> component, @NotNull ComponentInteractionEvent event, @NotNull CachedResponse entry, @NotNull Optional<CachedResponse> followup) {
        entry.setBusy(); // reset acknowledgment state for this interaction (each event has its own ack budget)
        X context = component.createContext(this.getDiscordBot(), event, entry.getResponse(), followup);

        Mono<Void> deferEdit = context.deferEdit(); // idempotent: a no-op once the interaction is acknowledged

        return (component.isDeferEdit() ? deferEdit : Mono.<Void>empty())
            .then(Mono.defer(() -> component.getInteraction().apply(context)))
            .checkpoint("ComponentListener#dispatchInline Processing")
            .onErrorResume(throwable -> this.onDispatchError(event, context, throwable))
            .then(this.editIfModified(entry, context, followup));
    }

    /**
     * Annotation dispatch: builds the component's context and invokes the {@link ComponentDispatcher}
     * route, editing the response if the handler modified it and decorating the pipeline with the
     * per-route cache TTL context key when the route declares one.
     */
    private <X extends ComponentContext> @NotNull Mono<Void> dispatchAnnotation(@NotNull EventInteractable<X> component, @NotNull ComponentInteractionEvent event, @NotNull CachedResponse entry, @NotNull ComponentDispatcher.ComponentRoute route, @NotNull Optional<CachedResponse> followup) {
        X context = component.createContext(this.getDiscordBot(), event, entry.getResponse(), followup);
        if (!route.getExpectedContextType().isInstance(context)) {
            this.warnContextMismatch(event, route);
            return this.dropInteraction(event);
        }

        entry.setBusy(); // reset acknowledgment state for this interaction (each event has its own ack budget)

        Mono<Void> dispatchMono = this.invokeRoute(route, context)
            .checkpoint("ComponentListener#dispatchAnnotation Processing")
            .onErrorResume(throwable -> this.onDispatchError(event, context, throwable))
            .then(this.editIfModified(entry, context, followup));

        return this.withCacheTtl(dispatchMono, route);
    }

    /**
     * Eternal dispatch: invoked when the response locator has no cached entry for the incoming event's
     * message. Resolves an annotation route; on a dispatchable route, synthesizes an eternal context
     * and invokes it. On a miss, ambiguous match, or context-type mismatch, the interaction is dropped.
     */
    private @NotNull Mono<Void> tryDispatchEternal(@NotNull ComponentInteractionEvent event) {
        RouteResolution resolution = this.resolveRoute(event);
        if (resolution.kind() != RouteResolution.Kind.DISPATCH)
            return this.dropInteraction(event);

        ComponentDispatcher.ComponentRoute route = resolution.route().orElseThrow();
        ComponentContext context = this.createEternalContext(event);
        if (!route.getExpectedContextType().isInstance(context)) {
            this.warnContextMismatch(event, route);
            return this.dropInteraction(event);
        }

        Mono<Void> dispatchMono = this.invokeRoute(route, context)
            .checkpoint("ComponentListener#tryDispatchEternal Processing")
            .onErrorResume(throwable -> this.onDispatchError(event, context, throwable))
            .then();

        return this.withCacheTtl(dispatchMono, route);
    }

    // --- shared dispatch helpers ---

    /**
     * Walks the response's current component tree (both the cached pagination components and the
     * current page's own components) for an {@link EventInteractable} whose identifier matches the
     * event's custom id.
     */
    private @NotNull Optional<EventInteractable<?>> matchComponent(@NotNull ComponentInteractionEvent event, @NotNull Response response) {
        return response.getCurrentComponents()
            .flatMap(Component::flattenComponents)
            .filter(EventInteractable.class::isInstance)
            .filter(UserInteractable.class::isInstance)
            .filter(component -> event.getCustomId().equals(((UserInteractable) component).getIdentifier()))
            .<EventInteractable<?>>map(component -> (EventInteractable<?>) component)
            .findFirst();
    }

    /**
     * Finalizes the interaction exactly once: editing the response through the interaction (which
     * clears the dirty flag internally) when the handler modified it, or otherwise recording the
     * interaction. Consolidating both outcomes here keeps the dispatch chains from double-finalizing.
     */
    private @NotNull Mono<Void> editIfModified(@NotNull CachedResponse entry, @NotNull ComponentContext context, @NotNull Optional<CachedResponse> followup) {
        return Mono.defer(() -> {
            if (entry.isModified())
                return followup.isEmpty() ? context.edit() : context.editFollowup();

            return entry.updateLastInteract().then();
        });
    }

    /** Drops an interaction that has no handler by acknowledging it with a deferred edit. */
    private @NotNull Mono<Void> dropInteraction(@NotNull ComponentInteractionEvent event) {
        return event.deferEdit().then();
    }

    /**
     * Acknowledges the interaction before reporting a handler error, so Discord does not surface
     * "interaction failed". Cached contexts acknowledge through the guarded cache entry; eternal
     * contexts fall back to the raw event. A failing acknowledgment is swallowed so it cannot mask
     * the original error.
     */
    private @NotNull Mono<Void> onDispatchError(@NotNull ComponentInteractionEvent event, @NotNull ComponentContext context, @NotNull Throwable throwable) {
        return Mono.defer(() -> context.findResponseCacheEntry().isPresent() ? context.deferEdit() : event.deferEdit())
            .onErrorResume(ignored -> Mono.empty())
            .then(this.reportException(context, throwable));
    }

    /** Forwards a handler error to the bot's exception handler with this listener's title. */
    private @NotNull Mono<Void> reportException(@NotNull ComponentContext context, @NotNull Throwable throwable) {
        return this.getDiscordBot().getExceptionHandler().handleException(
            ExceptionContext.of(
                this.getDiscordBot(),
                context,
                throwable,
                String.format("%s Exception", this.getTitle())
            )
        );
    }

    /** Logs a warning when a matched annotation route expects a different context type than the interaction builds. */
    private void warnContextMismatch(@NotNull ComponentInteractionEvent event, @NotNull ComponentDispatcher.ComponentRoute route) {
        this.getLog().warn(
            "@Component route '{}' on {} expects {} but the matched component does not build that context type",
            event.getCustomId(),
            route.getOwnerClass().getName(),
            route.getExpectedContextType().getSimpleName()
        );
    }

    /** Invokes an annotation route's method handle and adapts its publisher to a {@code Mono<Void>}. */
    @SuppressWarnings("unchecked")
    private @NotNull Mono<Void> invokeRoute(@NotNull ComponentDispatcher.ComponentRoute route, @NotNull ComponentContext context) {
        return Mono.from((Publisher<Void>) tryInvoke(route, context));
    }

    /** Decorates a dispatch pipeline with the route's cache time-to-live context key when it declares one. */
    private @NotNull Mono<Void> withCacheTtl(@NotNull Mono<Void> dispatch, @NotNull ComponentDispatcher.ComponentRoute route) {
        if (route.getCacheTtl() <= 0)
            return dispatch;

        Duration ttl = Duration.ofSeconds(route.getCacheTtl());
        return dispatch.contextWrite(reactorCtx -> reactorCtx.put(ComponentRouteTtlContextKey.KEY, ttl));
    }

    /**
     * Synthesizes the eternal context for an annotation-dispatched interaction whose backing message
     * has no cache entry, building a minimal stub component carrying the {@code customId} (and any
     * submitted values) whose responseId is the deterministic id from
     * {@link #computeEternalResponseId(ComponentInteractionEvent)}.
     */
    private @NotNull ComponentContext createEternalContext(@NotNull ComponentInteractionEvent event) {
        UUID eternalResponseId = computeEternalResponseId(event);

        if (event instanceof ButtonInteractionEvent buttonEvent)
            return ButtonContext.ofEternal(this.getDiscordBot(), buttonEvent, syntheticButton(event.getCustomId()), eternalResponseId);

        if (event instanceof SelectMenuInteractionEvent selectEvent)
            return SelectMenuContext.ofEternal(this.getDiscordBot(), selectEvent, syntheticSelectMenu(selectEvent), eternalResponseId);

        if (event instanceof ModalSubmitInteractionEvent modalEvent)
            return ModalContext.ofEternal(this.getDiscordBot(), modalEvent, syntheticModal(event.getCustomId()), eternalResponseId);

        throw new IllegalStateException("Unsupported component interaction event type: " + event.getClass().getName());
    }

    /** Wraps an entry as a followup when it represents one, so followup edits target the correct message. */
    private static @NotNull Optional<CachedResponse> followupOf(@NotNull CachedResponse entry) {
        return entry.isFollowup() ? Optional.of(entry) : Optional.empty();
    }

    /** Builds a stub button carrying only the interaction's custom id for an eternal dispatch. */
    private static @NotNull Button syntheticButton(@NotNull String customId) {
        return Button.builder()
            .withIdentifier(customId)
            .withStyle(Button.Style.SECONDARY)
            .withLabel("eternal")
            .build();
    }

    /** Builds a stub string menu carrying the interaction's custom id and submitted values for an eternal dispatch. */
    private static @NotNull SelectMenu syntheticSelectMenu(@NotNull SelectMenuInteractionEvent event) {
        return SelectMenu.StringMenu.builder()
            .withIdentifier(event.getCustomId())
            .build()
            .updateSelected(event.getValues());
    }

    /**
     * Builds a stub modal carrying the interaction's custom id for an eternal dispatch. The modal's
     * {@code Builder} validation requires a non-empty title and components, neither of which is
     * meaningful for a synthesized submit, so the modal is instantiated directly via its private
     * all-args constructor.
     */
    private static @NotNull Modal syntheticModal(@NotNull String customId) {
        return new Reflection<>(Modal.class).newInstance(
            customId,
            Optional.<String>empty(),
            (ConcurrentList<?>) Concurrent.newUnmodifiableList(),
            (Function<ModalContext, Mono<Void>>) ComponentContext::deferEdit
        );
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
     * Computes the deterministic eternal {@link UUID} for the given event's message snowflake. The
     * same message always yields the same UUID, so a synthesized context for an eternal interaction
     * is stable across dispatches.
     *
     * @param event the component interaction event
     * @return the deterministic eternal response id
     */
    private static @NotNull UUID computeEternalResponseId(@NotNull ComponentInteractionEvent event) {
        return UUID.nameUUIDFromBytes(("eternal:" + event.getMessageId().asLong()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Outcome of resolving an annotation route for an incoming interaction:
     * {@link Kind#DISPATCH} carries the route to invoke, {@link Kind#DROP} signals an ambiguous route
     * the caller must drop, and {@link Kind#MISS} signals no route matched (callers may fall back to
     * inline dispatch).
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
