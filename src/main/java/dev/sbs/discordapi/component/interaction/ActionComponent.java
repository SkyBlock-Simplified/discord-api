package dev.sbs.discordapi.component.interaction;

import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.layout.ActionRow;
import dev.sbs.discordapi.component.type.UserInteractComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Capability interface for interactive Discord components that accept user input.
 *
 * <p>
 * Narrows the return type of {@link Component#getD4jComponent()} to Discord4J's
 * {@link ActionComponent}.
 *
 * @see ActionRow
 */
public interface ActionComponent extends Component, UserInteractComponent {

    /** {@inheritDoc} */
    @NotNull discord4j.core.object.component.ActionComponent getD4jComponent();

}
