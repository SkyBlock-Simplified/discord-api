package dev.sbs.discordapi.context.message.text;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.data.Argument;
import dev.sbs.discordapi.command.relationship.Relationship;
import dev.sbs.discordapi.context.CommandContext;
import dev.sbs.discordapi.context.message.MessageContext;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;

public interface TextCommandContext extends CommandContext<MessageCreateEvent>, MessageContext<MessageCreateEvent> {

    @Override
    default @NotNull Snowflake getChannelId() {
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
    default @NotNull User getInteractUser() {
        return new User(this.getEvent().getClient(), this.getEvent().getMessage().getUserData());
    }

    @Override
    default @NotNull Snowflake getInteractUserId() {
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

    static TextCommandContext of(DiscordBot discordBot, MessageCreateEvent event, Relationship.Command commandRelationship, String commandAlias, ConcurrentList<Argument> arguments) {
        return new TextCommandContextImpl(discordBot, event, commandRelationship, commandAlias, arguments);
    }

}