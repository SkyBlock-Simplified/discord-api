package dev.sbs.discordapi.component.type;

import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.interaction.Modal;

/**
 * Pure marker interface for components valid at the top level of a Discord modal.
 *
 * <p>
 * Top-level modal components are those that can appear directly in a {@link Modal}'s
 * component list without being nested inside another layout. Unlike
 * {@link TopLevelMessageComponent}, this interface does not narrow the Discord4J return
 * type because the D4J {@code TopLevelModalComponent} is itself a pure marker that not
 * all valid modal components extend.
 *
 * @see Modal
 */
public interface TopLevelModalComponent extends Component {

}
