package dev.simplified.discordapi.component.capability;

import dev.simplified.discordapi.component.interaction.Modal;
import dev.simplified.discordapi.context.component.ModalContext;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * Capability interface for components that process their own submitted value when the
 * enclosing {@link Modal} is submitted.
 *
 * <p>
 * A modal never carries an interaction handler of its own - instead each modal-embedded
 * component declares how it processes its folded value, mirroring
 * {@link dev.simplified.discordapi.component.interaction.TextInput.SearchType}. When the modal
 * is submitted, {@link Modal#getInteraction()} folds the submitted values into the components
 * (via {@link ModalUpdatable}) and then invokes {@link #processModalSubmit(ModalContext)} on
 * each processable component in order.
 *
 * @see ModalUpdatable
 * @see Modal
 */
public interface ModalProcessable {

    /**
     * Processes this component's folded value on modal submit.
     *
     * <p>
     * A no-op when the component carries no submit processor.
     *
     * @param context the modal submit context whose modal holds the folded values
     * @return the reactive completion of the processing
     */
    @NotNull Mono<Void> processModalSubmit(@NotNull ModalContext context);

    /**
     * Binds a whole-context submit processor, ignoring the component itself.
     *
     * <p>
     * Used by {@link Modal.Builder#onSubmit(Function)} to attach a handler that reads the whole
     * submitted modal rather than this single component's value.
     *
     * @param processor the processor invoked with the modal submit context
     */
    void bindSubmitProcessor(@NotNull Function<ModalContext, Mono<Void>> processor);

}
