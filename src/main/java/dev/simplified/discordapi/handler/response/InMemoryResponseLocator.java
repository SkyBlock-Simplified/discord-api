package dev.simplified.discordapi.handler.response;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.discordapi.context.EventContext;
import dev.simplified.discordapi.handler.ComponentRouteTtlContextKey;
import dev.simplified.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory {@link ResponseLocator} backed by two maps: a primary
 * {@code uniqueId -> CachedResponse} map and a {@code messageId -> uniqueId}
 * secondary index. Both lookups are O(1).
 *
 * <p>
 * The {@link #store} method honors a per-route cache time-to-live override
 * carried on the Reactor {@link reactor.util.context.Context Context} under
 * {@link ComponentRouteTtlContextKey#KEY}. When present, the override replaces
 * the expiry derived from {@link Response#getTimeToLive() Response.timeToLive};
 * otherwise the response's own value is used.
 */
public final class InMemoryResponseLocator implements ResponseLocator {

    private final @NotNull ConcurrentMap<UUID, CachedResponse> entries = Concurrent.newMap();
    private final @NotNull ConcurrentMap<Snowflake, UUID> messageIndex = Concurrent.newMap();

    @Override
    public @NotNull Mono<CachedResponse> findByMessage(@NotNull Snowflake messageId) {
        return Mono.justOrEmpty(this.messageIndex.get(messageId))
            .mapNotNull(this.entries::get);
    }

    @Override
    public @NotNull Mono<CachedResponse> findByResponseId(@NotNull UUID responseId) {
        return Mono.justOrEmpty(this.entries.get(responseId));
    }

    @Override
    public @NotNull Mono<CachedResponse> findFollowupByIdentifier(@NotNull UUID parentId, @NotNull String identifier) {
        return Mono.justOrEmpty(this.entries.values()
            .stream()
            .filter(entry -> entry.getParentId().filter(parentId::equals).isPresent())
            .filter(entry -> entry.getFollowupIdentifier().filter(identifier::equals).isPresent())
            .findFirst()
        );
    }

    @Override
    public @NotNull Mono<CachedResponse> findFollowupByMessage(@NotNull UUID parentId, @NotNull Snowflake messageId) {
        return this.findByMessage(messageId)
            .filter(entry -> entry.getParentId().filter(parentId::equals).isPresent());
    }

    @Override
    public @NotNull Mono<CachedResponse> store(@NotNull Message message, @NotNull EventContext<?> creatorContext, @NotNull Response response) {
        return Mono.deferContextual(reactorCtx -> {
            Optional<Duration> ttlOverride = reactorCtx.<Duration>getOrEmpty(ComponentRouteTtlContextKey.KEY);

            CachedResponse.Builder builder = CachedResponse.builder()
                .withUniqueId(response.getUniqueId())
                .withMessageId(message.getId())
                .withChannelId(message.getChannelId())
                .withUserId(creatorContext.getInteractUserId())
                .withGuildId(creatorContext.getGuildId())
                .withBuilderKey(response.getBuilderKey())
                .withResponse(response)
                .withCreatedAt(Instant.now())
                .withExpiresAt(this.computeExpiresAt(response, ttlOverride));

            return Mono.just(this.put(builder.build()));
        });
    }

    @Override
    public @NotNull Mono<CachedResponse> storeFollowup(
        @NotNull CachedResponse parent,
        @NotNull String identifier,
        @NotNull Message message,
        @NotNull EventContext<?> creatorContext,
        @NotNull Response response
    ) {
        return Mono.deferContextual(reactorCtx -> {
            Optional<Duration> ttlOverride = reactorCtx.<Duration>getOrEmpty(ComponentRouteTtlContextKey.KEY);

            CachedResponse.Builder builder = CachedResponse.builder()
                .withUniqueId(response.getUniqueId())
                .withMessageId(message.getId())
                .withChannelId(message.getChannelId())
                .withUserId(creatorContext.getInteractUserId())
                .withGuildId(creatorContext.getGuildId())
                .withParentId(parent.getUniqueId())
                .withFollowupIdentifier(identifier)
                .withResponse(response)
                .withCreatedAt(Instant.now())
                .withExpiresAt(this.computeExpiresAt(response, ttlOverride));

            return Mono.just(this.put(builder.build()));
        });
    }

    @Override
    public @NotNull Mono<Void> evict(@NotNull UUID responseId) {
        return Mono.fromRunnable(() -> {
            CachedResponse removed = this.entries.remove(responseId);
            if (removed != null)
                this.messageIndex.remove(removed.getMessageId());

            // Cascade-delete followups that referenced this entry
            this.entries.values()
                .stream()
                .filter(entry -> entry.getParentId().filter(responseId::equals).isPresent())
                .toList()
                .forEach(followup -> {
                    this.entries.remove(followup.getUniqueId());
                    this.messageIndex.remove(followup.getMessageId());
                });
        });
    }

    @Override
    public @NotNull Mono<Void> deleteByMessage(@NotNull Snowflake messageId) {
        return Mono.defer(() -> {
            UUID responseId = this.messageIndex.get(messageId);
            return responseId != null ? this.evict(responseId) : Mono.empty();
        });
    }

    @Override
    public @NotNull Mono<CachedResponse> seed(@NotNull CachedResponse entry) {
        return Mono.fromCallable(() -> {
            CachedResponse canonical = this.entries.computeIfAbsent(entry.getUniqueId(), id -> entry);
            this.messageIndex.put(canonical.getMessageId(), canonical.getUniqueId());
            return canonical;
        });
    }

    @Override
    public @NotNull Mono<Void> update(@NotNull CachedResponse entry) {
        // Entries are held by reference; mutations on the entry object are
        // immediately visible. Nothing to do here.
        return Mono.empty();
    }

    @Override
    public @NotNull Flux<CachedResponse> findExpired() {
        return Flux.fromIterable(this.entries.values())
            .filter(CachedResponse::notActive);
    }

    /** Records the given entry in both maps and returns it. */
    @NotNull CachedResponse put(@NotNull CachedResponse entry) {
        this.entries.put(entry.getUniqueId(), entry);
        this.messageIndex.put(entry.getMessageId(), entry.getUniqueId());
        return entry;
    }

    /**
     * Computes the optional expiration instant from the response's
     * time-to-live, applying the optional per-route override when present.
     * Non-positive values indicate no expiry.
     */
    private @NotNull Optional<Instant> computeExpiresAt(@NotNull Response response, @NotNull Optional<Duration> ttlOverride) {
        if (ttlOverride.isPresent()) {
            Duration override = ttlOverride.get();
            if (override.isZero() || override.isNegative())
                return Optional.empty();

            return Optional.of(Instant.now().plus(override));
        }

        int ttl = response.getTimeToLive();
        if (ttl <= 0)
            return Optional.empty();

        return Optional.of(Instant.now().plus(Duration.ofSeconds(ttl)));
    }

}
