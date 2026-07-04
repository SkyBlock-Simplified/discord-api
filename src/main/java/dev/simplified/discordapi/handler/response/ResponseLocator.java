package dev.simplified.discordapi.handler.response;

import dev.simplified.discordapi.context.EventContext;
import dev.simplified.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive abstraction over the discord-api response cache. Default
 * implementation is an in-memory locator indexed by {@code messageId} and
 * {@code uniqueId} for O(1) lookup.
 *
 * @see CachedResponse
 */
public interface ResponseLocator {

    /**
     * Resolves the cached entry to dispatch an incoming component interaction against, transparently
     * hydrating and seeding an eternal entry on a hot-tier miss so callers always receive a real
     * entry to dispatch. The default resolves the hot tier only; the composite locator overrides it
     * to add cold-tier hydration. An empty result means the interaction has no owner and is dropped.
     *
     * @param event the incoming component interaction event
     * @return a mono emitting the entry to dispatch, or empty when none can be resolved
     */
    default Mono<CachedResponse> findForInteraction(@NotNull ComponentInteractionEvent event) {
        return this.findByMessage(event.getMessageId());
    }

    /**
     * Looks up a cached entry by Discord message snowflake.
     *
     * @param messageId the Discord message snowflake
     * @return a mono emitting the matching entry, or empty if none exists
     */
    Mono<CachedResponse> findByMessage(@NotNull Snowflake messageId);

    /**
     * Looks up a cached entry by its stable {@link Response#getUniqueId() response id}.
     *
     * @param responseId the stable response identifier
     * @return a mono emitting the matching entry, or empty if none exists
     */
    Mono<CachedResponse> findByResponseId(@NotNull UUID responseId);

    /**
     * Looks up a followup entry by its parent's response id and the
     * user-supplied followup identifier.
     *
     * @param parentId the {@link Response#getUniqueId() uniqueId} of the parent entry
     * @param identifier the user-supplied followup identifier
     * @return a mono emitting the matching followup entry, or empty if none exists
     */
    Mono<CachedResponse> findFollowupByIdentifier(@NotNull UUID parentId, @NotNull String identifier);

    /**
     * Looks up a followup entry by its parent's response id and the followup's
     * Discord message snowflake.
     *
     * @param parentId the {@link Response#getUniqueId() uniqueId} of the parent entry
     * @param messageId the Discord message snowflake of the followup message
     * @return a mono emitting the matching followup entry, or empty if none exists
     */
    Mono<CachedResponse> findFollowupByMessage(@NotNull UUID parentId, @NotNull Snowflake messageId);

    /**
     * Stores a newly-sent message and its {@link Response} in the cache.
     *
     * @param message the Discord message that was sent
     * @param creatorContext the context in which the message was created
     * @param response the response state being cached
     * @return a mono emitting the newly stored entry
     */
    Mono<CachedResponse> store(@NotNull Message message, @NotNull EventContext<?> creatorContext, @NotNull Response response);

    /**
     * Stores a new followup as an independent top-level entry with
     * {@code parentId} pointing at the parent's {@link Response#getUniqueId()}.
     *
     * @param parent the parent cached entry
     * @param identifier the user-supplied followup identifier
     * @param message the newly-sent followup Discord message
     * @param creatorContext the context in which the followup was created
     * @param response the followup response state
     * @return a mono emitting the newly stored followup entry
     */
    Mono<CachedResponse> storeFollowup(
        @NotNull CachedResponse parent,
        @NotNull String identifier,
        @NotNull Message message,
        @NotNull EventContext<?> creatorContext,
        @NotNull Response response
    );

    /**
     * Evicts a live entry from the cache by stable response id, cascading to its followups. This is
     * a hot-tier drop only: a cold-tier implementation persisting an eternal response is a no-op, so
     * an evicted eternal survives and re-hydrates on its next interaction. Used by the expiry reaper
     * and by temporary-response cleanup.
     *
     * @param responseId the stable response id to evict
     * @return a mono completing when eviction finishes
     */
    Mono<Void> evict(@NotNull UUID responseId);

    /**
     * Deletes the response backing the given message from every tier - the hot live entry (and its
     * followups) and any durable cold record. Message-keyed because a rebooted eternal may be
     * cold-only with no hot entry to resolve an id from. Used by message-delete teardown.
     *
     * @param messageId the Discord message snowflake whose response should be torn down
     * @return a mono completing when teardown finishes
     */
    Mono<Void> deleteByMessage(@NotNull Snowflake messageId);

    /**
     * Caches a pre-built entry and returns the <b>canonical</b> instance for its id - the value the
     * cache actually holds, which under a concurrent seed is the first winner, not necessarily the
     * argument. The composite uses this to promote a cold-tier hit into the hot tier while keeping
     * concurrent first-interactions converged on one entry. A tier that holds no live entries
     * returns the argument unchanged.
     *
     * @param entry the entry to cache
     * @return a mono emitting the canonical cached entry
     */
    Mono<CachedResponse> seed(@NotNull CachedResponse entry);

    /**
     * Persists mutable changes to an existing entry (navigation state in particular). Hot tiers hold
     * entries by reference so this is a no-op there; a cold tier write-through persists the eternal's
     * navigation coordinate when it changed.
     *
     * @param entry the entry whose in-memory state has been mutated
     * @return a mono completing when the update finishes
     */
    Mono<Void> update(@NotNull CachedResponse entry);

    /**
     * Enumerates entries whose {@link CachedResponse#getExpiresAt() expiresAt}
     * has passed OR whose time-to-live has elapsed since the last interaction.
     * Used by the scheduled cleanup loop to disable components and evict
     * expired entries.
     *
     * @return a flux of expired entries
     */
    Flux<CachedResponse> findExpired();

}
