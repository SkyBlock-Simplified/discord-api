package dev.sbs.discordapi.component.type;

import dev.sbs.discordapi.component.layout.Label;

/**
 * Capability interface for interactive components that can be wrapped in a {@link Label}
 * layout.
 *
 * <p>
 * A label layout pairs a descriptive text label with an interactive component, providing
 * additional context for the user. Components implementing this interface declare that they
 * are valid targets for label wrapping, carry a {@link UserInteractComponent#getIdentifier()
 * custom_id} for interaction routing, and can update their state from modal submission data
 * via {@link ModalUpdatable#updateFromModalData}.
 *
 * @see Label
 * @see ModalUpdatable
 */
public interface LabelComponent extends ModalUpdatable, UserInteractComponent {

}
