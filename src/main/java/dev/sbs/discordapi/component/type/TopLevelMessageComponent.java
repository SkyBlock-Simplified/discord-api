package dev.sbs.discordapi.component.type;

import dev.sbs.discordapi.component.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Capability interface for components valid at the top level of a Discord message.
 *
 * <p>
 * Top-level message components are those that can appear directly in a message's component
 * list without being nested inside another layout. Implementations narrow the Discord4J
 * return type to {@link discord4j.core.object.component.TopLevelMessageComponent}.
 */
public interface TopLevelMessageComponent extends Component {

    /** {@inheritDoc} */
    @Override
    @NotNull discord4j.core.object.component.TopLevelMessageComponent getD4jComponent();

}
