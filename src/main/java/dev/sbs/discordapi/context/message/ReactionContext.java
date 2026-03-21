package dev.sbs.discordapi.context.message;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.ReactionUserEmojiEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Specialization of {@link MessageContext} for reaction add and remove events, providing
 * access to the reacted {@link Emoji} and the reaction {@link Type}, along with methods
 * to manage reactions on the underlying message and cached response.
 *
 * <p>
 * Wraps a {@link ReactionUserEmojiEvent} and delegates channel, guild, and user resolution
 * to that event. Reaction removal methods update both the Discord message and the cached
 * {@link CachedResponse} page state.
 *
 * @see MessageContext
 * @see Emoji
 * @see Type
 */
public interface ReactionContext extends MessageContext<ReactionUserEmojiEvent> {

    /**
     * Returns the {@link Emoji} that was added or removed.
     *
     * @return the reaction emoji
     */
    @NotNull Emoji getEmoji();

    /**
     * Returns the {@link Type} of reaction event - either {@link Type#ADD} or
     * {@link Type#REMOVE}.
     *
     * @return the reaction type
     */
    @NotNull ReactionContext.Type getType();

    /** {@inheritDoc} */
    @Override
    default @NotNull Snowflake getChannelId() {
        return this.getEvent().getChannelId();
    }

    /** {@inheritDoc} */
    @Override
    default Mono<Guild> getGuild() {
        return this.getEvent().getGuild();
    }

    /** {@inheritDoc} */
    @Override
    default Optional<Snowflake> getGuildId() {
        return this.getEvent().getGuildId();
    }

    /** {@inheritDoc} */
    @Override
    default @NotNull User getInteractUser() {
        return Objects.requireNonNull(this.getEvent().getUser().block());
    }

    /** {@inheritDoc} */
    @Override
    default @NotNull Snowflake getInteractUserId() {
        return this.getEvent().getUserId();
    }

    /** {@inheritDoc} */
    @Override
    default Mono<Message> getMessage() {
        return this.getEvent().getMessage();
    }

    /** {@inheritDoc} */
    @Override
    default Snowflake getMessageId() {
        return this.getEvent().getMessageId();
    }

    /**
     * Checks whether this reaction is a Discord super reaction.
     *
     * @return {@code true} if the reaction is a super reaction
     */
    default boolean isSuperReaction() {
        return this.getEvent().isSuperReaction();
    }

    /**
     * Removes all instances of this reaction's {@link Emoji} from the message and clears
     * all reactions from the current page in the cached response.
     *
     * @return a {@link Mono} completing when the reactions have been removed and the cache updated
     */
    default Mono<Void> removeReactions() {
        return this.getMessage()
            .flatMap(message -> message.removeReactions(this.getEmoji().getD4jReaction()))
            .then(this.withResponseEntry(entry -> entry.updateResponse(
                entry.getResponse()
                    .mutate()
                    .editPage(
                        entry.getResponse()
                            .getHistoryHandler()
                            .getCurrentPage()
                            .mutate()
                            .clearReactions()
                            .build()
                    )
                    .build()
            )));
    }

    /**
     * Removes all instances of this reaction's {@link Emoji} from the message and clears
     * only this emoji from the current page in the cached response.
     *
     * @return a {@link Mono} completing when the reaction has been removed and the cache updated
     */
    default Mono<Void> removeReaction() {
        return this.getMessage()
            .flatMap(message -> message.removeReactions(this.getEmoji().getD4jReaction()))
            .then(this.withResponseEntry(entry -> entry.updateResponse(
                entry.getResponse()
                    .mutate()
                    .editPage(
                        entry.getResponse()
                            .getHistoryHandler()
                            .getCurrentPage()
                            .mutate()
                            .clearReaction(this.getEmoji())
                            .build()
                    )
                    .build()
            )));
    }

    /**
     * Removes only the interacting user's reaction for this {@link Emoji} from the message.
     *
     * @return a {@link Mono} completing when the user's reaction has been removed
     */
    default Mono<Void> removeUserReaction() {
        return this.getMessage().flatMap(message -> message.removeReaction(this.getEmoji().getD4jReaction(), this.getInteractUserId()));
    }

    /**
     * Removes the bot's own reaction for this {@link Emoji} from the message and clears
     * this emoji from the current page in the cached response.
     *
     * @return a {@link Mono} completing when the self-reaction has been removed and the cache updated
     */
    default Mono<Void> removeSelfReaction() {
        return this.getMessage()
            .flatMap(message -> message.removeSelfReaction(this.getEmoji().getD4jReaction()))
            .then(this.withResponseEntry(entry -> entry.updateResponse(
                entry.getResponse()
                    .mutate()
                    .editPage(
                        entry.getResponse()
                            .getHistoryHandler()
                            .getCurrentPage()
                            .mutate()
                            .clearReaction(this.getEmoji())
                            .build()
                    )
                    .build()
            )));
    }

    /**
     * Creates a new {@link ReactionContext} from the given event and metadata.
     *
     * @param discordBot the bot instance
     * @param event the reaction event
     * @param cachedMessage the cached response associated with the reacted message
     * @param emoji the emoji that was reacted
     * @param type the reaction type (add or remove)
     * @param followup an optional followup associated with the reacted message
     * @return a new reaction context
     */
    static @NotNull ReactionContext of(
        @NotNull DiscordBot discordBot,
        @NotNull ReactionUserEmojiEvent event,
        @NotNull Response cachedMessage,
        @NotNull Emoji emoji,
        @NotNull Type type,
        @NotNull Optional<Followup> followup
    ) {
        return new Impl(
            discordBot,
            event,
            cachedMessage.getUniqueId(),
            emoji,
            type,
            followup
        );
    }

    /**
     * Default implementation of {@link ReactionContext} that stores the bot instance,
     * reaction event, response id, emoji, reaction type, and optional followup.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements ReactionContext {

        /**
         * The bot instance that received this event.
         */
        private final @NotNull DiscordBot discordBot;

        /**
         * The underlying reaction event.
         */
        private final @NotNull ReactionUserEmojiEvent event;

        /**
         * The unique identifier of the cached response associated with the reacted message.
         */
        private final @NotNull UUID responseId;

        /**
         * The emoji that was added or removed.
         */
        private final @NotNull Emoji emoji;

        /**
         * The reaction type - add or remove.
         */
        private final @NotNull Type type;

        /**
         * The default followup associated with this context, if any.
         */
        private final @NotNull Optional<Followup> followup;

    }

    /**
     * Enumeration of reaction event types.
     */
    enum Type {

        /**
         * A reaction was added to a message.
         */
        ADD,

        /**
         * A reaction was removed from a message.
         */
        REMOVE

    }

}
