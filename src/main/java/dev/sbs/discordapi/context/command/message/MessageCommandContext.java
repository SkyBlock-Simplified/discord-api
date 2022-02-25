package dev.sbs.discordapi.context.command.message;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.Command;
import dev.sbs.discordapi.command.data.Argument;
import dev.sbs.discordapi.context.command.CommandContext;
import dev.sbs.discordapi.context.message.MessageContext;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import reactor.core.publisher.Mono;

import java.util.Optional;

public interface MessageCommandContext extends CommandContext<MessageCreateEvent>, MessageContext<MessageCreateEvent> {

    @Override
    default Snowflake getChannelId() {
        return this.getEvent().getMessage().getChannelId();
    }

    @Override
    default Mono<Guild> getGuild() {
        return this.getEvent().getGuild();
    }

    @Override
    default Optional<Snowflake> getGuildId() {
        return this.getEvent().getGuildId();
    }

    @Override
    default Mono<User> getInteractUser() {
        return this.getMessage().flatMap(message -> Mono.justOrEmpty(message.getAuthor()));
    }

    @Override
    default Snowflake getInteractUserId() {
        return Snowflake.of(this.getEvent().getMessage().getUserData().id());
    }

    @Override
    default Mono<Message> getMessage() {
        return Mono.just(this.getEvent().getMessage());
    }

    @Override
    default Snowflake getMessageId() {
        return this.getEvent().getMessage().getId();
    }

    @Override
    default boolean isSlashCommand() {
        return false;
    }

    static MessageCommandContext of(DiscordBot discordBot, MessageCreateEvent event, Command.Relationship commandRelationship, String commandAlias, ConcurrentList<Argument> arguments) {
        return new MessageCommandContextImpl(discordBot, event, commandRelationship, commandAlias, arguments);
    }

}