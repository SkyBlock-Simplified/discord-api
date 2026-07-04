package dev.simplified.discordapi.handler.response;

import dev.simplified.discordapi.handler.DiscordConfig;
import discord4j.common.util.Snowflake;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Pluggable cold-tier persistence boundary for eternal responses - the seam that lets eternal
 * messages survive bot restarts while keeping the framework storage-agnostic.
 *
 * <p>
 * The framework defines this interface and ships {@link InMemoryEternalResponseRepository} as a
 * no-durability default. Consumers wire a database-backed implementation (for example Hibernate)
 * through {@link DiscordConfig.Builder#withEternalRepository} when they need cross-restart survival;
 * everything inside this module talks to the interface only. Only the small
 * {@link EternalResponseRecord} coordinate ever crosses this boundary - never the rendered
 * response, its component tree, or any Discord4J spec.
 *
 * <p>
 * Reactive semantics mirror {@link ResponseLocator}: an empty {@link Mono} signals not-found, and
 * reactive types let consumers schedule blocking I/O onto whichever scheduler suits their backend.
 *
 * @see EternalResponseRecord
 * @see CompositeResponseLocator
 */
public interface EternalResponseRepository {

    /**
     * Looks up an eternal record by the Discord message snowflake backing it. This is the
     * hydration entry point - the component listener has the message id but no cache entry.
     *
     * @param messageId the Discord message snowflake
     * @return a mono emitting the matching record, or empty if none exists
     */
    @NotNull Mono<EternalResponseRecord> findByMessage(@NotNull Snowflake messageId);

    /**
     * Looks up an eternal record by its stable response id. Used by the refresh path.
     *
     * @param responseId the stable response id
     * @return a mono emitting the matching record, or empty if none exists
     */
    @NotNull Mono<EternalResponseRecord> findByResponseId(@NotNull UUID responseId);

    /**
     * Persists or replaces the record for an eternal response, keyed by
     * {@link EternalResponseRecord#responseId()}.
     *
     * @param record the record to store
     * @return a mono completing when the write is durable
     */
    @NotNull Mono<Void> save(@NotNull EternalResponseRecord record);

    /**
     * Updates only the persisted navigation coordinate for the given response, leaving the rest
     * of the record intact. Called after a navigating interaction so a later reboot replays the
     * page the user last landed on.
     *
     * @param responseId the stable response id
     * @param navState the navigation coordinate to persist
     * @return a mono completing when the write is durable
     */
    @NotNull Mono<Void> saveNavState(@NotNull UUID responseId, @NotNull NavState navState);

    /**
     * Deletes the record for the given response, so a deleted message is not resurrected by a
     * later interaction. A no-op when no record exists.
     *
     * @param responseId the stable response id
     * @return a mono completing when the delete is durable
     */
    @NotNull Mono<Void> delete(@NotNull UUID responseId);

}
