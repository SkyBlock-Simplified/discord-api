package dev.sbs.discordapi.component.type;

import org.jetbrains.annotations.NotNull;

/**
 * Capability interface for components that support user interaction.
 *
 * <p>
 * Every interactive component carries a unique identifier corresponding to the Discord
 * {@code custom_id} field, used to route interaction events back to the originating component.
 */
public interface UserInteractComponent {

    /** The unique {@code custom_id} string identifying this interactive component. */
    @NotNull String getIdentifier();

}
