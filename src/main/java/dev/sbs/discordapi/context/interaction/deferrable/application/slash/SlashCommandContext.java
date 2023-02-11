package dev.sbs.discordapi.context.interaction.deferrable.application.slash;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.data.Argument;
import dev.sbs.discordapi.command.relationship.Relationship;
import dev.sbs.discordapi.context.CommandContext;
import dev.sbs.discordapi.context.interaction.deferrable.DeferrableInteractionContext;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import reactor.core.publisher.Mono;

import java.util.Optional;

public interface SlashCommandContext extends CommandContext<ChatInputInteractionEvent>, DeferrableInteractionContext<ChatInputInteractionEvent> {

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
        // Guild in ChatInputInteractionEvent#getInteraction Empty
        return Mono.justOrEmpty(this.getGuildId()).flatMap(guildId -> this.getDiscordBot().getGateway().getGuildById(guildId));
    }

    @Override
    default Optional<Snowflake> getGuildId() {
        return this.getEvent().getInteraction().getGuildId();
    }

    @Override
    default User getInteractUser() {
        return this.getEvent().getInteraction().getUser();
    }

    @Override
    default Snowflake getInteractUserId() {
        return this.getEvent().getInteraction().getUser().getId();
    }

    @Override
    default Mono<Message> getReply() {
        return this.getEvent().getReply();
    }

    @Override
    default boolean isSlashCommand() {
        return true;
    }

    static SlashCommandContext of(DiscordBot discordBot, ChatInputInteractionEvent event, Relationship.Command commandRelationship, String commandAlias, ConcurrentList<Argument> arguments) {
        return new SlashCommandContextImpl(discordBot, event, commandRelationship, commandAlias, arguments);
    }

    @Override
    default Mono<Void> interactionReply(InteractionApplicationCommandCallbackSpec interactionApplicationCommandCallbackSpec) {
        return this.getEvent().reply(interactionApplicationCommandCallbackSpec);
    }

}