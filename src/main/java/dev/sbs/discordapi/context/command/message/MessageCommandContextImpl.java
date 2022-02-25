package dev.sbs.discordapi.context.command.message;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.Command;
import dev.sbs.discordapi.command.data.Argument;
import discord4j.core.event.domain.message.MessageCreateEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
class MessageCommandContextImpl implements MessageCommandContext {

    @Getter private final DiscordBot discordBot;
    @Getter private final MessageCreateEvent event;
    @Getter private final Command.Relationship relationship;
    @Getter private final String commandAlias;
    @Getter private final ConcurrentList<Argument> arguments;

}