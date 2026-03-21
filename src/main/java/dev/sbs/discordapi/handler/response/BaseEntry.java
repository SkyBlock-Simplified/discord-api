package dev.sbs.discordapi.handler.response;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Message;
import discord4j.core.object.reaction.Reaction;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Abstract base for cached response entries, associating a {@link Response}
 * with its Discord channel, user, and message {@link Snowflake} identifiers.
 *
 * <p>
 * Subclasses track the last-rendered state ({@code currentResponse}) alongside
 * the latest state ({@code response}) to support dirty-checking via
 * {@link #isModified()}, and provide reactive helpers for updating attachments
 * and reactions on the underlying Discord message.
 *
 * @see CachedResponse
 * @see Followup
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class BaseEntry {

    /** Discord channel snowflake where the response message resides. */
    private final @NotNull Snowflake channelId;

    /** Discord user snowflake of the user who owns this response. */
    private final @NotNull Snowflake userId;

    /** Discord message snowflake of the response message. */
    private final @NotNull Snowflake messageId;

    /** Current (possibly updated) response state. */
    private @NotNull Response response;

    /** Snapshot of the response state as last sent to Discord. */
    @Getter(AccessLevel.PROTECTED)
    private @NotNull Response currentResponse;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BaseEntry baseEntry = (BaseEntry) o;

        return Objects.equals(this.getChannelId(), baseEntry.getChannelId())
            && Objects.equals(this.getUserId(), baseEntry.getUserId())
            && Objects.equals(this.getMessageId(), baseEntry.getMessageId())
            && Objects.equals(this.getResponse(), baseEntry.getResponse())
            && Objects.equals(this.getCurrentResponse(), baseEntry.getCurrentResponse());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getChannelId(), this.getUserId(), this.getMessageId(), this.getResponse(), this.getCurrentResponse());
    }

    /**
     * Returns whether this entry represents a followup message.
     *
     * @return {@code true} if this entry is a followup
     */
    public abstract boolean isFollowup();

    /**
     * Checks whether the current response state differs from the
     * last-rendered snapshot, or the response has flagged itself
     * as requiring a cache update.
     *
     * @return {@code true} if the response has been modified since last render
     */
    public boolean isModified() {
        return !this.getCurrentResponse().equals(this.getResponse()) || this.getResponse().isCacheUpdateRequired();
    }

    /**
     * Synchronizes the last-rendered snapshot with the current response
     * state and clears the cache-update flag.
     */
    protected void processLastInteract() {
        this.currentResponse = this.response;
        this.response.setNoCacheUpdateRequired();
    }

    /**
     * Replaces the current response with the given updated response.
     *
     * @param response the new response state
     */
    protected void setUpdatedResponse(@NotNull Response response) {
        this.response = response;
    }

    /**
     * Updates the attachment references on this entry's response from
     * the given Discord message.
     *
     * @param message the Discord message containing updated attachments
     * @return a mono that completes when attachments have been updated
     */
    public Mono<CachedResponse> updateAttachments(@NotNull Message message) {
        return Mono.fromRunnable(() -> this.getResponse().updateAttachments(message));
    }

    /**
     * Synchronizes the Discord message's reactions with those defined on
     * the current response page, removing stale reactions and adding
     * missing ones.
     *
     * @param message the Discord message to update reactions on
     * @return a mono emitting this entry when reaction updates complete
     */
    public Mono<BaseEntry> updateReactions(@NotNull Message message) {
        return Mono.just(message)
            .checkpoint("ResponseHandler#updateReactions Processing")
            .flatMap(msg -> {
                // Update Reactions
                ConcurrentList<Emoji> newReactions = this.getResponse()
                    .getHistoryHandler()
                    .getCurrentPage()
                    .getReactions();

                // Current Reactions
                ConcurrentList<Emoji> currentReactions = msg.getReactions()
                    .stream()
                    .filter(Reaction::selfReacted)
                    .map(Reaction::getEmoji)
                    .map(Emoji::of)
                    .collect(Concurrent.toList());

                Mono<Void> mono = Mono.empty();

                // Remove Existing Reactions
                if (currentReactions.stream().anyMatch(messageEmoji -> !newReactions.contains(messageEmoji)))
                    mono = msg.removeAllReactions();

                return mono.then(Mono.when(
                    newReactions.stream()
                        .map(emoji -> msg.addReaction(emoji.getD4jReaction()))
                        .collect(Concurrent.toList())
                ));
            })
            .thenReturn(this);
    }

}