package dev.sbs.discordapi.context.deferrable.component.modal;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.deferrable.component.ComponentContext;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.Modal;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Context for modal submission interactions, extending {@link ComponentContext} with
 * access to the submitted {@link Modal} and its user-provided input values.
 *
 * <p>
 * Instances are created via the {@link #of} factory method when a
 * {@link ModalSubmitInteractionEvent} is dispatched. The modal's components are
 * automatically updated with the submitted values during construction.
 */
public interface ModalContext extends ComponentContext {

    /**
     * Returns the underlying {@link ModalSubmitInteractionEvent}.
     *
     * @return the modal submit interaction event
     */
    @Override
    @NotNull ModalSubmitInteractionEvent getEvent();

    /**
     * Returns the submitted {@link Modal} with its components updated to reflect
     * the user's input values.
     *
     * @return the submitted modal with updated component values
     */
    @Override
    @NotNull Modal getComponent();

    /**
     * Creates a new {@code ModalContext} for the given event, response, and modal. The
     * modal's components are updated with the submitted values from the event.
     *
     * @param discordBot the bot instance
     * @param event the modal submit interaction event
     * @param response the cached response that presented the modal
     * @param modal the original modal definition
     * @param followup the associated followup, if any
     * @return a new modal context with updated component values
     */
    static @NotNull ModalContext of(@NotNull DiscordBot discordBot, @NotNull ModalSubmitInteractionEvent event, @NotNull Response response, @NotNull Modal modal, @NotNull Optional<Followup> followup) {
        return new Impl(
            discordBot,
            event,
            response.getUniqueId(),
            modal.mutate()
                .updateComponents(event)
                .build(),
            followup
        );
    }

    /**
     * Default implementation of {@link ModalContext}.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements ModalContext {

        /** The bot instance. */
        private final @NotNull DiscordBot discordBot;

        /** The underlying modal submit interaction event. */
        private final @NotNull ModalSubmitInteractionEvent event;

        /** The unique response identifier linking this context to its cached response. */
        private final @NotNull UUID responseId;

        /** The submitted modal with updated component values. */
        private final @NotNull Modal component;

        /** The associated followup, if any. */
        private final @NotNull Optional<Followup> followup;

    }

}
