package dev.sbs.discordapi.context.message.text;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.data.Argument;
import dev.sbs.discordapi.command.relationship.Relationship;
import discord4j.core.event.domain.message.MessageCreateEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
class TextCommandContextImpl implements TextCommandContext {

    @Getter private final @NotNull DiscordBot discordBot;
    @Getter private final @NotNull MessageCreateEvent event;
    @Getter private final @NotNull UUID responseId = UUID.randomUUID();
    @Getter private final @NotNull Relationship.Command relationship;
    @Getter private final @NotNull String commandAlias;
    @Getter private final @NotNull ConcurrentList<Argument> arguments;

}