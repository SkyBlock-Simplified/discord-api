package dev.sbs.discordapi.component.scope;

import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.interaction.Modal;

/**
 * Placement scope for components valid at the top level of a Discord modal.
 *
 * <p>
 * Only {@link dev.sbs.discordapi.component.layout.Label Label} and
 * {@link dev.sbs.discordapi.component.TextDisplay TextDisplay} implement this interface.
 * Interactive components such as {@link dev.sbs.discordapi.component.interaction.TextInput
 * TextInput} and {@link dev.sbs.discordapi.component.interaction.SelectMenu SelectMenu} must
 * be wrapped in a {@code Label} to appear in a modal.
 *
 * @see Modal
 */
public interface TopLevelModalComponent extends Component {

}
