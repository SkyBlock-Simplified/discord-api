package dev.sbs.discordapi.component.type;

import dev.sbs.discordapi.component.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Capability interface for components valid at the top level of a Discord modal.
 *
 * <p>
 * Top-level modal components are those that can appear directly in a modal's component
 * list without being nested inside another layout. Implementations narrow the Discord4J
 * return type to {@link discord4j.core.object.component.TopLevelModalComponent}.
 *
 * @see dev.sbs.discordapi.component.interaction.Modal
 */
public interface TopLevelModalComponent extends Component {

    /** {@inheritDoc} */
    @Override
    @NotNull discord4j.core.object.component.TopLevelModalComponent getD4jComponent();

}
