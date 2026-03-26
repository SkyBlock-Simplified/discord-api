package dev.sbs.discordapi.component.type;

import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.layout.Label;

/**
 * Capability interface for interactive components that can be wrapped in a {@link Label}
 * layout.
 *
 * <p>
 * A label layout pairs a descriptive text label with an interactive component, providing
 * additional context for the user. Components implementing this interface declare that they
 * are valid targets for label wrapping and carry a {@link UserInteractComponent#getIdentifier()
 * custom_id} for interaction routing.
 *
 * @see Label
 */
public interface LabelComponent extends Component, UserInteractComponent {

}
