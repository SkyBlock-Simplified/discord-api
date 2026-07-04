package dev.simplified.discordapi.handler.response;

import discord4j.common.util.Snowflake;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimal cold-tier coordinate for an eternal response - the only state that crosses the
 * {@link EternalResponseRepository} boundary. The full {@link dev.simplified.discordapi.response.Response}
 * is never serialized; it is rebuilt on demand from the registered
 * {@link dev.simplified.discordapi.listener.Eternal @Eternal} builder keyed by {@link #builderKey()},
 * seeded with the opaque {@link #payload()} the builder produced at creation, and restored to the
 * page position captured in {@link #navState()}.
 *
 * @param responseId the stable {@link dev.simplified.discordapi.response.Response#getUniqueId() response id}, assigned at creation and reused on every hydration
 * @param messageId the Discord message snowflake backing this eternal response
 * @param channelId the Discord channel snowflake containing the message
 * @param guildId the Discord guild snowflake, or empty for direct-message contexts
 * @param userId the Discord user snowflake that created the eternal response
 * @param builderKey the stable key of the registered builder that reconstructs the response
 * @param payload the opaque builder input captured at creation; the framework never interprets it
 * @param navState the persisted navigation coordinate to replay onto the rebuilt response
 * @param createdAt the instant this eternal response was first stored
 * @param updatedAt the instant this record was last written
 */
public record EternalResponseRecord(
    @NotNull UUID responseId,
    @NotNull Snowflake messageId,
    @NotNull Snowflake channelId,
    @NotNull Optional<Snowflake> guildId,
    @NotNull Snowflake userId,
    @NotNull String builderKey,
    @NotNull String payload,
    @NotNull NavState navState,
    @NotNull Instant createdAt,
    @NotNull Instant updatedAt
) {

    /**
     * Returns a copy of this record with the given navigation coordinate and update timestamp,
     * leaving every other component unchanged.
     *
     * @param navState the navigation coordinate to persist
     * @param updatedAt the write timestamp
     * @return the updated record
     */
    public @NotNull EternalResponseRecord withNavState(@NotNull NavState navState, @NotNull Instant updatedAt) {
        return new EternalResponseRecord(
            this.responseId,
            this.messageId,
            this.channelId,
            this.guildId,
            this.userId,
            this.builderKey,
            this.payload,
            navState,
            this.createdAt,
            updatedAt
        );
    }

}
