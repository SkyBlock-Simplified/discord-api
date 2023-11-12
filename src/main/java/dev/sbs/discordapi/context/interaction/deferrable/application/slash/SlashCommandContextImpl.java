package dev.sbs.discordapi.context.interaction.deferrable.application.slash;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.reference.SlashCommandReference;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
class SlashCommandContextImpl implements SlashCommandContext {

    @Getter private final @NotNull DiscordBot discordBot;
    @Getter private final @NotNull ChatInputInteractionEvent event;
    @Getter private final @NotNull UUID responseId = UUID.randomUUID();
    @Getter private final @NotNull SlashCommandReference command;
    @Getter private final @NotNull ConcurrentList<Argument> arguments;

}