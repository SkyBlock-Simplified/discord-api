package dev.sbs.discordapi.component.capability;

import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

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
 * acknowledges the interaction before the handler executes.
 *
 * @param <T> the context subtype this component's handler accepts
 */
public interface EventInteractable<T> {

    /** The reactive handler function invoked when a user interacts with this component. */
    @NotNull Function<T, Mono<Void>> getInteraction();

    /** Whether this component's interaction should be automatically deferred as an edit. */
    boolean isDeferEdit();

}
