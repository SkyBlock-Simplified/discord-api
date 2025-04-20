package dev.sbs.discordapi.context.deferrable.command;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.Structure;
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
    @NotNull Structure getStructure();

    default @NotNull Message getTargetMessage() {
        return this.getEvent().getResolvedMessage();
    }

    default @NotNull Snowflake getTargetMessageId() {
        return this.getEvent().getTargetId();
    }

    static @NotNull MessageCommandContext of(@NotNull DiscordBot discordBot, @NotNull MessageInteractionEvent event, @NotNull Structure structure) {
        return new Impl(discordBot, event, structure);
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements MessageCommandContext {

        private final @NotNull DiscordBot discordBot;
        private final @NotNull MessageInteractionEvent event;
        private final @NotNull UUID responseId = UUID.randomUUID();
        private final @NotNull Structure structure;

    }

}