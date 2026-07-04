package dev.simplified.discordapi.handler.response;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.discordapi.handler.DiscordConfig;
import discord4j.common.util.Snowflake;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Process-local default {@link EternalResponseRepository}: keeps every {@link EternalResponseRecord} in
 * a primary {@code responseId -> record} map plus a {@code messageId -> responseId} index, mirroring
 * the two-map shape of {@link InMemoryResponseLocator}. State is lost on bot restart.
 *
 * <p>
 * Useful for unit tests, the offline harness, and ephemeral bots. Production deployments that need
 * eternal messages to survive restarts plug a durable implementation through
 * {@link DiscordConfig.Builder#withEternalRepository}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InMemoryEternalResponseRepository implements EternalResponseRepository {

    private final @NotNull ConcurrentMap<UUID, EternalResponseRecord> records = Concurrent.newMap();
    private final @NotNull ConcurrentMap<Snowflake, UUID> messageIndex = Concurrent.newMap();

    /**
     * Constructs a fresh, empty store.
     *
     * @return a new in-memory store
     */
    public static @NotNull InMemoryEternalResponseRepository of() {
        return new InMemoryEternalResponseRepository();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<EternalResponseRecord> findByMessage(@NotNull Snowflake messageId) {
        return Mono.justOrEmpty(this.messageIndex.get(messageId))
            .mapNotNull(this.records::get);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<EternalResponseRecord> findByResponseId(@NotNull UUID responseId) {
        return Mono.justOrEmpty(this.records.get(responseId));
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<Void> save(@NotNull EternalResponseRecord record) {
        return Mono.fromRunnable(() -> {
            this.records.put(record.responseId(), record);
            this.messageIndex.put(record.messageId(), record.responseId());
        });
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<Void> saveNavState(@NotNull UUID responseId, @NotNull NavState navState) {
        return Mono.fromRunnable(() -> {
            EternalResponseRecord existing = this.records.get(responseId);
            if (existing != null)
                this.records.put(responseId, existing.withNavState(navState, Instant.now()));
        });
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<Void> delete(@NotNull UUID responseId) {
        return Mono.fromRunnable(() -> {
            EternalResponseRecord removed = this.records.remove(responseId);
            if (removed != null)
                this.messageIndex.remove(removed.messageId());
        });
    }

}
