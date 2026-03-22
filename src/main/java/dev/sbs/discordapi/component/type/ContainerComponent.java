package dev.sbs.discordapi.component.type;

import dev.sbs.discordapi.component.Component;

/**
 * Marker interface for components that can be placed inside a
 * {@link dev.sbs.discordapi.component.layout.Container Container} layout.
 *
 * <p>
 * A container groups related components together under a single visual boundary
 * in Discord's Components V2 system. Only components implementing this interface
 * are accepted as children of a container.
 *
 * @see dev.sbs.discordapi.component.layout.Container
 */
public interface ContainerComponent extends Component {

}
