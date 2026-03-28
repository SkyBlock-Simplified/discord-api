package dev.sbs.discordapi.component.capability;

import dev.sbs.discordapi.component.Component;
import discord4j.discordjson.json.ComponentData;
import org.jetbrains.annotations.NotNull;

/**
 * Capability interface for components that can update their state from modal submission
 * data ({@link ComponentData} payload).
 *
 * <p>
 * When a modal is submitted, Discord delivers a {@link ComponentData} payload for each
 * interactive component the user interacted with. Components implementing this interface
 * can consume that payload to synchronize their internal state with the submitted values.
 *
 * @see Component
 */
public interface ModalUpdatable {

    /**
     * Updates this component's state from the given modal submission data.
     *
     * @param data the Discord component data from the modal submit event
     */
    void updateFromData(@NotNull ComponentData data);

}
