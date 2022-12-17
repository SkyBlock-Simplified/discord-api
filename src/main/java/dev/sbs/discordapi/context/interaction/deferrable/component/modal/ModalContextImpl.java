package dev.sbs.discordapi.context.interaction.deferrable.component.modal;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.response.component.interaction.Modal;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
class ModalContextImpl implements ModalContext {

    @Getter private final DiscordBot discordBot;
    @Getter private final ModalSubmitInteractionEvent event;
    @Getter private final UUID uniqueId = UUID.randomUUID();
    @Getter private final UUID responseId;
    @Getter private final Modal component;

}