package dev.sbs.discordapi.handler.response;

import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Cached entry for a followup message associated with a parent
 * {@link CachedResponse}, identified by a unique string identifier
 * in addition to its Discord message {@link Snowflake}.
 *
 * @see ResponseEntry
 * @see CachedResponse
 */
@Getter
public class ResponseFollowup implements ResponseEntry {

    /** Discord channel snowflake where the response message resides. */
    private final @NotNull Snowflake channelId;

    /** Discord user snowflake of the user who owns this response. */
    private final @NotNull Snowflake userId;

    /** Discord message snowflake of the response message. */
    private final @NotNull Snowflake messageId;

    /** Current (possibly updated) response state. */
    private @NotNull Response response;

    /** Snapshot of the response state as last sent to Discord. */
    private @NotNull Response currentResponse;

    /** Unique string identifier for this followup within its parent response. */
    private final @NotNull String identifier;

    /**
     * Constructs a new {@code Followup} with the given identifier and
     * Discord coordinates.
     *
     * @param identifier the unique followup identifier
     * @param channelId the Discord channel snowflake
     * @param userId the user snowflake
     * @param messageId the Discord message snowflake
     * @param response the followup response
     */
    ResponseFollowup(@NotNull String identifier, @NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
        this.identifier = identifier;
        this.channelId = channelId;
        this.userId = userId;
        this.messageId = messageId;
        this.response = response;
        this.currentResponse = response;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ResponseFollowup responseFollowup = (ResponseFollowup) o;

        return Objects.equals(this.getChannelId(), responseFollowup.getChannelId())
            && Objects.equals(this.getUserId(), responseFollowup.getUserId())
            && Objects.equals(this.getMessageId(), responseFollowup.getMessageId())
            && Objects.equals(this.getResponse(), responseFollowup.getResponse())
            && Objects.equals(this.getCurrentResponse(), responseFollowup.getCurrentResponse())
            && Objects.equals(this.getIdentifier(), responseFollowup.getIdentifier());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getChannelId(), this.getUserId(), this.getMessageId(), this.getResponse(), this.getCurrentResponse(), this.getIdentifier());
    }

    @Override
    public boolean isFollowup() {
        return true;
    }

    /**
     * Replaces this entry's response with the given updated response.
     *
     * @param response the new response state
     * @return a mono emitting this followup
     */
    public Mono<ResponseFollowup> updateResponse(@NotNull Response response) {
        this.response = response;
        return Mono.just(this);
    }

}
