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
 * @see BaseEntry
 * @see CachedResponse
 */
@Getter
public class Followup extends BaseEntry {

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
    Followup(@NotNull String identifier, @NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
        super(channelId, userId, messageId, response, response);
        this.identifier = identifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        Followup followup = (Followup) o;

        return Objects.equals(this.getIdentifier(), followup.getIdentifier());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.getIdentifier());
    }

    /** {@inheritDoc} */
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
    public Mono<Followup> updateResponse(@NotNull Response response) {
        super.setUpdatedResponse(response);
        return Mono.just(this);
    }

}