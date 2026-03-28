package dev.sbs.discordapi.component.scope;

import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.layout.Section;

/**
 * Placement scope for components that can be placed inside a {@link Section} layout as
 * primary content.
 *
 * <p>
 * A section pairs one or more content components with an optional
 * {@link AccessoryComponent accessory}. Only components implementing this interface
 * are accepted as the primary content children of a section.
 *
 * @see Section
 * @see AccessoryComponent
 */
public interface SectionComponent extends Component {

}
