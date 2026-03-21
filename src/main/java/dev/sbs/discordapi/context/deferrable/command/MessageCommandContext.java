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

/**
 * Context for message command interactions (right-click on a message), extending {@link CommandContext}
 * with access to the targeted {@link Message} and its identifier.
 *
 * <p>
 * Instances are created via the {@link #of} factory method when a {@link MessageInteractionEvent}
 * is dispatched.
 */
public interface MessageCommandContext extends CommandContext<MessageInteractionEvent> {

    /**
     * {@inheritDoc}
     */
    @Override
    @NotNull Structure getStructure();

    /**
     * Returns the resolved {@link Message} that was targeted by this message command.
     *
     * @return the targeted message
     */
    default @NotNull Message getTargetMessage() {
        return this.getEvent().getResolvedMessage();
    }

    /**
     * Returns the snowflake identifier of the targeted message.
     *
     * @return the target message id
     */
    default @NotNull Snowflake getTargetMessageId() {
        return this.getEvent().getTargetId();
    }

    /**
     * Creates a new {@code MessageCommandContext} for the given event.
     *
     * @param discordBot the bot instance
     * @param event the message interaction event
     * @param structure the command structure metadata
     * @return a new message command context
     */
    static @NotNull MessageCommandContext of(@NotNull DiscordBot discordBot, @NotNull MessageInteractionEvent event, @NotNull Structure structure) {
        return new Impl(discordBot, event, structure);
    }

    /**
     * Default implementation of {@link MessageCommandContext}.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements MessageCommandContext {

        /** The bot instance. */
        private final @NotNull DiscordBot discordBot;

        /** The underlying message interaction event. */
        private final @NotNull MessageInteractionEvent event;

        /** The unique response identifier for this context. */
        private final @NotNull UUID responseId = UUID.randomUUID();

        /** The command structure metadata. */
        private final @NotNull Structure structure;

    }

}