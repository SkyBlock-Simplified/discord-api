package dev.sbs.discordapi.context.interaction.deferrable.application;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.reference.MessageCommandReference;
import dev.sbs.discordapi.context.CommandContext;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.MessageInteractionEvent;
import discord4j.core.object.entity.Message;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface MessageCommandContext extends CommandContext<MessageInteractionEvent> {

    @Override
    @NotNull MessageCommandReference getCommand();

    default @NotNull Message getTargetMessage() {
        return this.getEvent().getResolvedMessage();
    }

    default @NotNull Snowflake getTargetMessageId() {
        return this.getEvent().getTargetId();
    }

    static @NotNull MessageCommandContext of(@NotNull DiscordBot discordBot, @NotNull MessageInteractionEvent event, @NotNull MessageCommandReference command) {
        return new Impl(discordBot, event, command);
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements MessageCommandContext {

        private final @NotNull DiscordBot discordBot;
        private final @NotNull MessageInteractionEvent event;
        private final @NotNull UUID responseId = UUID.randomUUID();
        private final @NotNull MessageCommandReference command;

    }
}