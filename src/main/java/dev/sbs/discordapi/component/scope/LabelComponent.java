package dev.sbs.discordapi.component.scope;

import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.capability.ModalUpdatable;
import dev.sbs.discordapi.component.capability.UserInteractable;
import dev.sbs.discordapi.component.layout.Label;

/**
 * Placement scope for interactive components that can be wrapped in a {@link Label} layout.
 *
 * <p>
 * A label layout pairs a descriptive text label with an interactive component, providing
 * additional context for the user. Components implementing this interface declare that they
 * are valid targets for label wrapping, carry a {@link UserInteractable#getIdentifier()
 * custom_id} for interaction routing, and can update their state from modal submission data
 * via {@link ModalUpdatable#updateFromData}.
 *
 * @see Label
 * @see ModalUpdatable
 */
public interface LabelComponent extends Component, ModalUpdatable, UserInteractable {

}
