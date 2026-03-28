package dev.sbs.discordapi.component.scope;

import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.interaction.Button;
import dev.sbs.discordapi.component.layout.Section;
import dev.sbs.discordapi.component.media.Thumbnail;
import discord4j.core.object.component.IAccessoryComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Placement scope for components that can serve as the accessory of a {@link Section} layout.
 *
 * <p>
 * A section accessory is a secondary visual element - such as a {@link Thumbnail} or
 * {@link Button} - displayed alongside the section's primary content. Implementations
 * narrow the Discord4J return type to {@link IAccessoryComponent}.
 *
 * @see Section
 * @see SectionComponent
 */
public interface AccessoryComponent extends Component {

    /** {@inheritDoc} */
    @Override
    @NotNull IAccessoryComponent getD4jComponent();

}
