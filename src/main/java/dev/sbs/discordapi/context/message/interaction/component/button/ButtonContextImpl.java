package dev.sbs.discordapi.context.message.interaction.component.button;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.response.component.action.Button;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
class ButtonContextImpl implements ButtonContext {

    @Getter private final DiscordBot discordBot;
    @Getter private final ButtonInteractionEvent event;
    @Getter private final UUID uniqueId = UUID.randomUUID();
    @Getter private final UUID responseId;
    @Getter private final Button component;

}