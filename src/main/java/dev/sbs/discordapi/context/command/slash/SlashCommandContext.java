package dev.sbs.discordapi.context.command.slash;

import dev.sbs.api.util.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.Command;
import dev.sbs.discordapi.command.data.Argument;
import dev.sbs.discordapi.context.command.CommandContext;
import dev.sbs.discordapi.context.message.interaction.ApplicationInteractionContext;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.InteractionCallbackSpec;
import reactor.core.publisher.Mono;

import java.util.Optional;

public interface SlashCommandContext extends CommandContext<ChatInputInteractionEvent>, ApplicationInteractionContext<ChatInputInteractionEvent> {

    default void deferReply(boolean ephemeral) {
        this.getEvent().deferReply(InteractionCallbackSpec.builder().ephemeral(ephemeral).build()).subscribe();
    }

    @Override
    default Mono<MessageChannel> getChannel() {
        return this.getEvent().getInteraction().getChannel();
    }

    @Override
    default Snowflake getChannelId() {
        return this.getEvent().getInteraction().getChannelId();
    }

    @Override
    default Mono<Guild> getGuild() {
        return Mono.justOrEmpty(this.getGuildId()).flatMap(guildId -> this.getDiscordBot().getGateway().getGuildById(guildId));
    }

    @Override
    default Optional<Snowflake> getGuildId() {
        return this.getEvent().getInteraction().getGuildId();
    }

    @Override
    default Mono<User> getInteractUser() {
        return Mono.just(this.getEvent().getInteraction().getUser());
    }

    @Override
    default Snowflake getInteractUserId() {
        return this.getEvent().getInteraction().getUser().getId();
    }

    @Override
    default Mono<Message> getReply() {
        return this.getEvent().getReply();
    }

    static SlashCommandContext of(DiscordBot discordBot, ChatInputInteractionEvent event, Command.Relationship commandRelationship, String commandAlias, ConcurrentList<Argument> arguments) {
        return new SlashCommandContextImpl(discordBot, event, commandRelationship, commandAlias, arguments);
    }

    @Override
    default Mono<Void> reply(InteractionApplicationCommandCallbackSpec interactionApplicationCommandCallbackSpec) {
        return this.getEvent().reply(interactionApplicationCommandCallbackSpec);
    }

}