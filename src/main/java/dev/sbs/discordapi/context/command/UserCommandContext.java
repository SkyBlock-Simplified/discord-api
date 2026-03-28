package dev.sbs.discordapi.context.command;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.Structure;
import dev.sbs.discordapi.context.scope.CommandContext;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.UserInteractionEvent;
import discord4j.core.object.entity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Context for user command interactions (right-click on a user), extending {@link CommandContext}
 * with access to the targeted {@link User} and their identifier.
 *
 * <p>
 * Instances are created via the {@link #of} factory method when a {@link UserInteractionEvent}
 * is dispatched.
 */
public interface UserCommandContext extends CommandContext<UserInteractionEvent> {

    /**
     * {@inheritDoc}
     */
    @Override
    @NotNull Structure getStructure();

    /** The resolved {@link User} that was targeted by this user command. */
    default @NotNull User getTargetUser() {
        return this.getEvent().getResolvedUser();
    }

    /** The snowflake identifier of the targeted user. */
    default @NotNull Snowflake getTargetUserId() {
        return this.getEvent().getTargetId();
    }

    /**
     * Creates a new {@code UserCommandContext} for the given event.
     *
     * @param discordBot the bot instance
     * @param event the user interaction event
     * @param structure the command structure metadata
     * @return a new user command context
     */
    static @NotNull UserCommandContext of(@NotNull DiscordBot discordBot, @NotNull UserInteractionEvent event, @NotNull Structure structure) {
        return new Impl(discordBot, event, structure);
    }

    /**
     * Default implementation of {@link UserCommandContext}.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements UserCommandContext {

        /** The bot instance. */
        private final @NotNull DiscordBot discordBot;

        /** The underlying user interaction event. */
        private final @NotNull UserInteractionEvent event;

        /** The unique response identifier for this context. */
        private final @NotNull UUID responseId = UUID.randomUUID();

        /** The command structure metadata. */
        private final @NotNull Structure structure;

    }

}