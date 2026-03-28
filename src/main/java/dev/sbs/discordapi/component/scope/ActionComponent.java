package dev.sbs.discordapi.component.scope;

import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.capability.UserInteractable;
import dev.sbs.discordapi.component.layout.ActionRow;
import org.jetbrains.annotations.NotNull;

/**
 * Placement scope for interactive components valid inside an {@link ActionRow} layout.
 *
 * <p>
 * Action components accept user input and carry a
 * {@link UserInteractable#getIdentifier() custom_id} for interaction routing.
 * Implementations narrow the Discord4J return type to
 * {@link discord4j.core.object.component.ActionComponent}.
 *
 * @see ActionRow
 */
public interface ActionComponent extends Component, UserInteractable {

    /** {@inheritDoc} */
    @NotNull discord4j.core.object.component.ActionComponent getD4jComponent();

}
