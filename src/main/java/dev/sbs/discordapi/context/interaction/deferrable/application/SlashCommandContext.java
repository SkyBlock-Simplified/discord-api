package dev.sbs.discordapi.context.interaction.deferrable.application;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.reference.CommandReference;
import dev.sbs.discordapi.command.reference.SlashCommandReference;
import dev.sbs.discordapi.context.CommandContext;
import dev.sbs.discordapi.context.interaction.deferrable.DeferrableInteractionContext;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

public interface SlashCommandContext extends CommandContext<ChatInputInteractionEvent>, DeferrableInteractionContext<ChatInputInteractionEvent> {

    @Override
    default Mono<MessageChannel> getChannel() {
        return this.getEvent().getInteraction().getChannel();
    }

    @Override
    @NotNull SlashCommandReference getCommand();

    @NotNull
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
    default @NotNull User getInteractUser() {
        return this.getEvent().getInteraction().getUser();
    }

    @Override
    default @NotNull Snowflake getInteractUserId() {
        return this.getEvent().getInteraction().getUser().getId();
    }

    @Override
    default Mono<Message> getReply() {
        return this.getEvent().getReply();
    }

    @Override
    default @NotNull CommandReference.Type getType() {
        return CommandReference.Type.CHAT_INPUT;
    }

    static @NotNull SlashCommandContext of(@NotNull DiscordBot discordBot, @NotNull ChatInputInteractionEvent event, @NotNull SlashCommandReference slashCommand, @NotNull ConcurrentList<Argument> arguments) {
        return new Impl(discordBot, event, slashCommand, arguments);
    }

    @Override
    default Mono<Void> interactionEdit(InteractionApplicationCommandCallbackSpec interactionApplicationCommandCallbackSpec) {
        return this.getEvent().reply(interactionApplicationCommandCallbackSpec);
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements SlashCommandContext {

        private final @NotNull DiscordBot discordBot;
        private final @NotNull ChatInputInteractionEvent event;
        private final @NotNull UUID responseId = UUID.randomUUID();
        private final @NotNull SlashCommandReference command;
        private final @NotNull ConcurrentList<Argument> arguments;

    }
}