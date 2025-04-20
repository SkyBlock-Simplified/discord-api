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

public interface ModalContext extends ComponentContext {

    @Override
    @NotNull ModalSubmitInteractionEvent getEvent();

    @Override
    @NotNull Modal getComponent();

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

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements ModalContext {

        private final @NotNull DiscordBot discordBot;
        private final @NotNull ModalSubmitInteractionEvent event;
        private final @NotNull UUID responseId;
        private final @NotNull Modal component;
        private final @NotNull Optional<Followup> followup;

    }

}
