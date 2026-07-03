package dev.simplified.discordapi.component.capability;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.context.scope.ComponentContext;
import dev.simplified.discordapi.handler.response.CachedResponse;
import dev.simplified.discordapi.response.Response;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Function;

/**
 * Capability interface for components that handle user interaction events via a reactive
 * handler function.
 *
 * <p>
 * An event-interactable component carries a reactive handler function that is invoked when
 * a user interacts with the component (e.g., clicks a button or submits a select menu). The
 * handler receives a typed context and returns a {@link Mono} representing the asynchronous
 * response.
 *
 * <p>
 * Components may opt into automatic deferred editing via {@link #isDeferEdit()}, which
 * acknowledges the interaction before the handler executes. Dispatchable components also build
 * their own typed context via {@link #createContext}, so the single component listener can
 * dispatch every component kind polymorphically.
 *
 * @param <T> the context subtype this component's handler accepts
 */
public interface EventInteractable<T extends ComponentContext> {

    /** The reactive handler function invoked when a user interacts with this component. */
    @NotNull Function<T, Mono<Void>> getInteraction();

    /** Whether this component's interaction should be automatically deferred as an edit. */
    boolean isDeferEdit();

    /**
     * Builds the typed interaction context for an interaction targeting this component, folding
     * in any Discord-side interaction values (for example a select menu's chosen values).
     *
     * <p>
     * Overridden by the dispatchable components (button, select menu, modal). Components that only
     * ever appear as modal-submit values, and so are never dispatched on their own, do not build a
     * context.
     *
     * @param discordBot the bot instance
     * @param event the component interaction event
     * @param response the cached response containing the component
     * @param followup the matched followup, if the interaction targets one
     * @return the constructed context
     */
    default @NotNull T createContext(@NotNull DiscordBot discordBot, @NotNull ComponentInteractionEvent event, @NotNull Response response, @NotNull Optional<CachedResponse> followup) {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + " does not build an interaction context");
    }

}
