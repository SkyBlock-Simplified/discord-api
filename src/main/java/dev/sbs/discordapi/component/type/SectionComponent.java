package dev.sbs.discordapi.component.type;

import dev.sbs.discordapi.component.Component;

/**
 * Marker interface for components that can be placed inside a
 * {@link dev.sbs.discordapi.component.layout.Section Section} layout as primary content.
 *
 * <p>
 * A section pairs one or more content components with an optional
 * {@link AccessoryComponent accessory}. Only components implementing this interface
 * are accepted as the primary content children of a section.
 *
 * @see dev.sbs.discordapi.component.layout.Section
 * @see AccessoryComponent
 */
public interface SectionComponent extends Component {

}
