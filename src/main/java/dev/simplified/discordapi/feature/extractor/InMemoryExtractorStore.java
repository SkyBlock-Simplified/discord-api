package dev.simplified.discordapi.feature.extractor;

import dev.simplified.discordapi.handler.DiscordConfig;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Process-local default {@link ExtractorStore}: keeps every {@link Extractor} in a
 * {@link ConcurrentHashMap} keyed by id. State is lost on bot restart.
 * <p>
 * Useful for unit tests, ephemeral bots, and as a placeholder in development before a
 * durable backend is wired. Production deployments should plug in a database-backed
 * implementation through {@link DiscordConfig.Builder}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InMemoryExtractorStore implements ExtractorStore {

    private final @NotNull ConcurrentMap<UUID, Extractor> rows = new ConcurrentHashMap<>();

    /**
     * Constructs a fresh, empty store.
     *
     * @return a new in-memory store
     */
    public static @NotNull InMemoryExtractorStore of() {
        return new InMemoryExtractorStore();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<Extractor> findById(@NotNull UUID id, long callerUserId, @Nullable Long callerGuildId) {
        Extractor row = this.rows.get(id);
        if (row == null || !row.canBeUsedBy(callerUserId, callerGuildId)) return Mono.empty();
        return Mono.just(row);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<Extractor> findByShortId(@NotNull String shortId, long callerUserId, @Nullable Long callerGuildId) {
        Extractor owner = null, guild = null, pub = null;
        for (Extractor row : this.rows.values()) {
            if (!row.getShortId().equals(shortId)) continue;
            switch (row.getVisibility()) {
                case PRIVATE -> {
                    if (row.getOwnerUserId() == callerUserId && owner == null) owner = row;
                }
                case GUILD -> {
                    if (row.getGuildId() != null && row.getGuildId().equals(callerGuildId) && guild == null) guild = row;
                }
                case PUBLIC -> {
                    if (pub == null) pub = row;
                }
            }
        }
        Extractor pick = owner != null ? owner : (guild != null ? guild : pub);
        return pick == null ? Mono.empty() : Mono.just(pick);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Flux<Extractor> findVisible(long callerUserId, @Nullable Long callerGuildId) {
        return Flux.fromIterable(this.rows.values())
            .filter(row -> row.canBeUsedBy(callerUserId, callerGuildId))
            .sort(Comparator.comparing(Extractor::getLabel));
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<Void> save(@NotNull Extractor extractor) {
        return Mono.fromRunnable(() -> {
            if (extractor.getCreatedAt().equals(Instant.EPOCH))
                extractor.setCreatedAt(Instant.now());
            if (extractor.getId().equals(new UUID(0L, 0L)))
                extractor.setId(UUID.randomUUID());
            this.rows.put(extractor.getId(), extractor);
        });
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<Boolean> deleteById(@NotNull UUID id, long callerUserId) {
        return Mono.fromCallable(() -> {
            Extractor row = this.rows.get(id);
            if (row == null || row.getOwnerUserId() != callerUserId) return false;
            return this.rows.remove(id) != null;
        });
    }

}
