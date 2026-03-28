package dev.sbs.discordapi.component.capability;

import org.jetbrains.annotations.NotNull;

/**
 * Capability interface for components that support user interaction via a unique
 * {@code custom_id} identifier for interaction routing.
 *
 * <p>
 * Every interactive component carries a unique identifier corresponding to the Discord
 * {@code custom_id} field, used to route interaction events back to the originating component.
 */
public interface UserInteractable {

    /** The unique {@code custom_id} string identifying this interactive component. */
    @NotNull String getIdentifier();

}
