package dev.simplified.discordapi.handler.response;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.context.EternalBuildContext;
import dev.simplified.discordapi.context.EventContext;
import dev.simplified.discordapi.handler.ComponentDispatcher;
import dev.simplified.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import dev.simplified.annotations.RequiredArgsConstructor;
import dev.simplified.annotations.Log;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cold-tier {@link ResponseLocator} that rebuilds eternal responses on demand from an
 * {@link EternalResponseRepository}. It is a peer of the hot {@link InMemoryResponseLocator} in a
 * {@link CompositeResponseLocator} chain, and the ONE place that knows how to rebuild a
 * {@link Response} from its persisted coordinate - the repository SPI stays "opaque record in,
 * opaque record out".
 *
 * <p>
 * Only {@link #findForInteraction} (rebuild), {@link #store}/{@link #update}/{@link #deleteByMessage}
 * (persist) do meaningful work; every other method is a truthful no-op - a cold tier has no live
 * message index, no followups, and no hot-expiry concept. In particular the plain finders return
 * empty so an incidental {@code findByMessage} (a reaction, a delete, an expiry sweep) never fires
 * an {@code @Eternal} builder; rebuilds happen only on the dispatch path.
 *
 * @see EternalResponseRepository
 * @see CompositeResponseLocator
 */
@Log
@RequiredArgsConstructor
public final class EternalResponseLocator implements ResponseLocator {

    private final @NotNull EternalResponseRepository repository;
    private final @NotNull Supplier<ComponentDispatcher> dispatcher;
    private final @NotNull DiscordBot discordBot;

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<CachedResponse> findForInteraction(@NotNull ComponentInteractionEvent event) {
        return this.repository.findByMessage(event.getMessageId())
            .flatMap(stored -> {
                Optional<ComponentDispatcher.EternalRoute> builder = this.dispatcher.get().findEternalBuilder(stored.builderKey());

                if (builder.isEmpty()) {
                    log.warn(
                        "No @Eternal builder registered for key '{}' (message {}); dropping interaction",
                        stored.builderKey(),
                        event.getMessageId().asString()
                    );
                    return Mono.empty();
                }

                return Mono.fromCallable(() -> this.rebuild(stored, event, builder.get()));
            });
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<CachedResponse> store(@NotNull Message message, @NotNull EventContext<?> creatorContext, @NotNull Response response) {
        return response.isEternal()
            ? this.repository.save(toRecord(message, creatorContext, response)).then(Mono.empty())
            : Mono.empty();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<Void> update(@NotNull CachedResponse entry) {
        if (!entry.isEternal())
            return Mono.empty();

        NavState captured = NavState.capture(entry.getResponse().getHistoryHandler());
        if (captured.equals(entry.getNavState()))
            return Mono.empty(); // nothing navigated - skip the durable write

        entry.setNavState(captured);
        return this.repository.saveNavState(entry.getUniqueId(), captured);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<Void> deleteByMessage(@NotNull Snowflake messageId) {
        return this.repository.findByMessage(messageId)
            .flatMap(stored -> this.repository.delete(stored.responseId()));
    }

    /** {@inheritDoc} - the cold tier holds no live entry, so it returns the argument unchanged. */
    @Override
    public @NotNull Mono<CachedResponse> seed(@NotNull CachedResponse entry) {
        return Mono.just(entry);
    }

    // --- no-ops: a cold tier has no live entries, followups, or hot-expiry ---

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<CachedResponse> findByMessage(@NotNull Snowflake messageId) {
        return Mono.empty();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<CachedResponse> findByResponseId(@NotNull UUID responseId) {
        return Mono.empty();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<CachedResponse> findFollowupByIdentifier(@NotNull UUID parentId, @NotNull String identifier) {
        return Mono.empty();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<CachedResponse> findFollowupByMessage(@NotNull UUID parentId, @NotNull Snowflake messageId) {
        return Mono.empty();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<CachedResponse> storeFollowup(
        @NotNull CachedResponse parent,
        @NotNull String identifier,
        @NotNull Message message,
        @NotNull EventContext<?> creatorContext,
        @NotNull Response response
    ) {
        return Mono.empty();
    }

    /** {@inheritDoc} - eternal entries survive the reaper; only {@link #deleteByMessage} removes the record. */
    @Override
    public @NotNull Mono<Void> evict(@NotNull UUID responseId) {
        return Mono.empty();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Flux<CachedResponse> findExpired() {
        return Flux.empty();
    }

    /** Invokes the builder, stamps identity and the eternal marker, restores nav state, and builds a hot entry. */
    private @NotNull CachedResponse rebuild(@NotNull EternalResponseRecord stored, @NotNull ComponentInteractionEvent event, @NotNull ComponentDispatcher.EternalRoute route) {
        User user = event.getInteraction().getUser();
        EternalBuildContext buildContext = EternalBuildContext.of(
            this.discordBot,
            event,
            stored.channelId(),
            stored.guildId(),
            user,
            stored.responseId(),
            stored.payload()
        );

        Response built = this.dispatcher.get().invokeEternalBuilder(route, buildContext);
        Response hydrated = built.mutate()
            .withUniqueId(stored.responseId())
            .asEternal(stored.builderKey(), stored.payload())
            .build();
        stored.navState().applyTo(hydrated.getHistoryHandler());

        return CachedResponse.builder()
            .withUniqueId(stored.responseId())
            .withMessageId(stored.messageId())
            .withChannelId(stored.channelId())
            .withUserId(stored.userId())
            .withGuildId(stored.guildId())
            .withBuilderKey(stored.builderKey())
            .withResponse(hydrated)
            .withNavState(stored.navState())
            .withState(CachedResponse.State.IDLE)
            .withCreatedAt(stored.createdAt())
            .build();
    }

    /** Snapshots an eternal reply into a cold record from the reply args (there is no hot entry here). */
    private static @NotNull EternalResponseRecord toRecord(@NotNull Message message, @NotNull EventContext<?> creatorContext, @NotNull Response response) {
        Instant now = Instant.now();
        return new EternalResponseRecord(
            response.getUniqueId(),
            message.getId(),
            message.getChannelId(),
            creatorContext.getGuildId(),
            creatorContext.getInteractUserId(),
            response.getBuilderKey().orElseThrow(),
            response.getPayload().orElse(""),
            NavState.capture(response.getHistoryHandler()),
            now,
            now
        );
    }

}
