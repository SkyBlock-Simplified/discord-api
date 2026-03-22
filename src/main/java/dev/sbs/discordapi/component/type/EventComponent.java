package dev.sbs.discordapi.component.type;

import dev.sbs.discordapi.context.component.ComponentContext;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * Capability interface for components that handle user interaction events.
 *
 * <p>
 * An event component carries a reactive handler function that is invoked when a user
 * interacts with the component (e.g., clicks a button or submits a select menu). The
 * handler receives a typed {@link ComponentContext} and returns a {@link Mono} representing
 * the asynchronous response.
 *
 * <p>
 * Components may opt into automatic deferred editing via {@link #isDeferEdit()}, which
 * acknowledges the interaction before the handler executes.
 *
 * @param <T> the specific {@link ComponentContext} subtype this component's handler accepts
 * @see ComponentContext
 */
public interface EventComponent<T extends ComponentContext> {

    /** The reactive handler function invoked when a user interacts with this component. */
    @NotNull Function<T, Mono<Void>> getInteraction();

    /** Whether this component's interaction should be automatically deferred as an edit. */
    boolean isDeferEdit();

}
