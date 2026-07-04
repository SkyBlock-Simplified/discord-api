package dev.simplified.discordapi.listener.component;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.component.Component;
import dev.simplified.discordapi.component.capability.EventInteractable;
import dev.simplified.discordapi.component.capability.UserInteractable;
import dev.simplified.discordapi.context.capability.ExceptionContext;
import dev.simplified.discordapi.context.scope.ComponentContext;
import dev.simplified.discordapi.handler.ComponentDispatcher;
import dev.simplified.discordapi.handler.ComponentRouteTtlContextKey;
import dev.simplified.discordapi.handler.response.CachedResponse;
import dev.simplified.discordapi.handler.response.ResponseLocator;
import dev.simplified.discordapi.listener.DiscordListener;
import dev.simplified.discordapi.response.Response;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Optional;

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
 *   <li><b>Annotation route</b> - the matched
 *       {@link dev.simplified.discordapi.listener.Component @Component} handler is invoked with a
 *       context built from the resolved response (a hot-tier hit or a transparently hydrated
 *       eternal)</li>
 *   <li><b>Inline component</b> - the resolved component's interaction lambda is invoked</li>
 *   <li><b>No owner</b> - when neither a hot entry nor an eternal record exists for the message,
 *       the interaction is dropped via {@code deferEdit().then()}</li>
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
            .findForInteraction(event)
            // thenReturn keeps the entry emitting so switchIfEmpty fires ONLY on a genuine miss (no hot
            // entry AND no eternal record) - handleEvent returns Mono<Void> (emits nothing), which would
            // otherwise always trip the drop and double-acknowledge the interaction.
            .flatMap(entry -> this.handleEvent(event, entry).thenReturn(entry))
            .switchIfEmpty(Mono.defer(() -> this.dropInteraction(event).then(Mono.empty())))
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
            .onErrorResume(throwable -> this.onDispatchError(context, throwable))
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
            .onErrorResume(throwable -> this.onDispatchError(context, throwable))
            .then(this.editIfModified(entry, context, followup));

        return this.withCacheTtl(dispatchMono, route);
    }

    // --- shared dispatch helpers ---

    /**
     * Walks the response's current component tree (both the cached pagination components and the
     * current page's own components) for an {@link EventInteractable} whose identifier matches the
     * event's custom id.
     */
    private @NotNull Optional<EventInteractable<?>> matchComponent(@NotNull ComponentInteractionEvent event, @NotNull Response response) {
        return response.getCurrentComponents(this.getDiscordBot().getEmojiHandler())
            .flatMap(Component::flattenComponents)
            .filter(EventInteractable.class::isInstance)
            .filter(UserInteractable.class::isInstance)
            .filter(component -> event.getCustomId().equals(((UserInteractable) component).getIdentifier()))
            .<EventInteractable<?>>map(component -> (EventInteractable<?>) component)
            .findFirst();
    }

    /**
     * Flushes any pending response edit the handler left behind, then finalizes the interaction exactly
     * once by returning the entry to {@link CachedResponse.State#IDLE IDLE}. Editing renders the content
     * without touching the acknowledgment state, so the single {@link CachedResponse#finalizeInteraction()}
     * here is the only place the dispatch returns to idle.
     */
    private @NotNull Mono<Void> editIfModified(@NotNull CachedResponse entry, @NotNull ComponentContext context, @NotNull Optional<CachedResponse> followup) {
        return Mono.defer(() -> entry.isModified()
                ? (followup.isEmpty() ? context.edit() : context.editFollowup())
                : Mono.<Void>empty())
            .then(entry.finalizeInteraction())
            // Write-through: persists an eternal's navigation coordinate when it changed (no-op for mortals).
            .then(this.getDiscordBot().getResponseLocator().update(entry))
            .then();
    }

    /** Drops an interaction that has no handler by acknowledging it with a deferred edit. */
    private @NotNull Mono<Void> dropInteraction(@NotNull ComponentInteractionEvent event) {
        return event.deferEdit().then();
    }

    /**
     * Acknowledges the interaction through its guarded cache entry before reporting a handler error,
     * so Discord does not surface "interaction failed". Every dispatched context now has a real entry
     * (hot or hydrated), so the acknowledgment always routes through the entry. A failing
     * acknowledgment is swallowed so it cannot mask the original error.
     */
    private @NotNull Mono<Void> onDispatchError(@NotNull ComponentContext context, @NotNull Throwable throwable) {
        return context.deferEdit()
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

    /** Wraps an entry as a followup when it represents one, so followup edits target the correct message. */
    private static @NotNull Optional<CachedResponse> followupOf(@NotNull CachedResponse entry) {
        return entry.isFollowup() ? Optional.of(entry) : Optional.empty();
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
