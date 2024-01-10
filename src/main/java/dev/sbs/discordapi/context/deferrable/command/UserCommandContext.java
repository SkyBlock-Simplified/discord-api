package dev.sbs.discordapi.context.deferrable.command;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.reference.UserCommandReference;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.UserInteractionEvent;
import discord4j.core.object.entity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface UserCommandContext extends CommandContext<UserInteractionEvent> {

    @Override
    @NotNull UserCommandReference getCommand();

    default @NotNull User getTargetUser() {
        return this.getEvent().getResolvedUser();
    }

    default @NotNull Snowflake getTargetUserId() {
        return this.getEvent().getTargetId();
    }

    static @NotNull UserCommandContext of(@NotNull DiscordBot discordBot, @NotNull UserInteractionEvent event, @NotNull UserCommandReference command) {
        return new Impl(discordBot, event, command);
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements UserCommandContext {

        private final @NotNull DiscordBot discordBot;
        private final @NotNull UserInteractionEvent event;
        private final @NotNull UUID responseId = UUID.randomUUID();
        private final @NotNull UserCommandReference command;

    }
}