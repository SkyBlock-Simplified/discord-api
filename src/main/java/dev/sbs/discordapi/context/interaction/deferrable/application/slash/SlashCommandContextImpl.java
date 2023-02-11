package dev.sbs.discordapi.context.interaction.deferrable.application.slash;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.data.Argument;
import dev.sbs.discordapi.command.relationship.Relationship;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
class SlashCommandContextImpl implements SlashCommandContext {

    @Getter private final DiscordBot discordBot;
    @Getter private final ChatInputInteractionEvent event;
    @Getter private final UUID uniqueId = UUID.randomUUID();
    @Getter private final Relationship.Command relationship;
    @Getter private final String commandAlias;
    @Getter private final ConcurrentList<Argument> arguments;

}