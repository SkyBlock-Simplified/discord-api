package dev.simplified.discordapi.handler.response;

import dev.simplified.collection.ConcurrentList;
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
 * A {@link ResponseLocator} that delegates to an ordered chain of tier locators (hot-first),
 * knowing nothing about them beyond the interface - modeled on
 * {@link dev.simplified.discordapi.handler.exception.CompositeExceptionHandler CompositeExceptionHandler}.
 *
 * <p>
 * Reads resolve to the first tier that answers. {@link #findForInteraction} additionally
 * <b>promotes</b> a later-tier hit up into the earlier tiers (read-through cache promotion) and
 * dispatches the {@link #seed seeded} canonical instance, so concurrent first-interactions on a
 * cold-only eternal converge on a single hot entry. Writes fan out to every tier - each tier decides
 * what it persists (the hot tier always caches a live entry; a cold tier persists only eternals).
 *
 * @see ResponseLocator
 * @see InMemoryResponseLocator
 * @see EternalResponseLocator
 */
public final class CompositeResponseLocator implements ResponseLocator {

    private final @NotNull ConcurrentList<ResponseLocator> tiers;

    /**
     * Constructs a composite over the given tier chain, ordered hottest-first.
     *
     * @param tiers the ordered tier locators
     */
    public CompositeResponseLocator(@NotNull ConcurrentList<ResponseLocator> tiers) {
        this.tiers = tiers;
    }

    // --- reads: first tier that answers ---

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<CachedResponse> findForInteraction(@NotNull ComponentInteractionEvent event) {
        return this.findForInteractionFrom(event, 0);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<CachedResponse> findByMessage(@NotNull Snowflake messageId) {
        return Flux.fromIterable(this.tiers).concatMap(tier -> tier.findByMessage(messageId)).next();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<CachedResponse> findByResponseId(@NotNull UUID responseId) {
        return Flux.fromIterable(this.tiers).concatMap(tier -> tier.findByResponseId(responseId)).next();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<CachedResponse> findFollowupByIdentifier(@NotNull UUID parentId, @NotNull String identifier) {
        return Flux.fromIterable(this.tiers).concatMap(tier -> tier.findFollowupByIdentifier(parentId, identifier)).next();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<CachedResponse> findFollowupByMessage(@NotNull UUID parentId, @NotNull Snowflake messageId) {
        return Flux.fromIterable(this.tiers).concatMap(tier -> tier.findFollowupByMessage(parentId, messageId)).next();
    }

    // --- writes: fan out to every tier ---

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<CachedResponse> store(@NotNull Message message, @NotNull EventContext<?> creatorContext, @NotNull Response response) {
        return Flux.fromIterable(this.tiers)
            .concatMap(tier -> tier.store(message, creatorContext, response))
            .collectList() // collectList (not next()) so a later tier's write is not cancelled
            .flatMap(entries -> entries.isEmpty() ? Mono.empty() : Mono.just(entries.getFirst()));
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
        return Flux.fromIterable(this.tiers)
            .concatMap(tier -> tier.storeFollowup(parent, identifier, message, creatorContext, response))
            .collectList()
            .flatMap(entries -> entries.isEmpty() ? Mono.empty() : Mono.just(entries.getFirst()));
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<Void> update(@NotNull CachedResponse entry) {
        return Flux.fromIterable(this.tiers).concatMap(tier -> tier.update(entry)).then();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<Void> evict(@NotNull UUID responseId) {
        return Flux.fromIterable(this.tiers).concatMap(tier -> tier.evict(responseId)).then();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<Void> deleteByMessage(@NotNull Snowflake messageId) {
        return Flux.fromIterable(this.tiers).concatMap(tier -> tier.deleteByMessage(messageId)).then();
    }

    /** {@inheritDoc} - seeds every tier and returns the hottest tier's canonical instance. */
    @Override
    public @NotNull Mono<CachedResponse> seed(@NotNull CachedResponse entry) {
        return Flux.fromIterable(this.tiers).concatMap(tier -> tier.seed(entry)).next();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Flux<CachedResponse> findExpired() {
        return Flux.fromIterable(this.tiers).flatMap(ResponseLocator::findExpired);
    }

    /**
     * Resolves {@link #findForInteraction} from the given tier index onward, promoting a hit into the
     * earlier tiers and dispatching the seeded canonical instance.
     */
    private @NotNull Mono<CachedResponse> findForInteractionFrom(@NotNull ComponentInteractionEvent event, int index) {
        if (index >= this.tiers.size())
            return Mono.empty();

        return this.tiers.get(index).findForInteraction(event)
            .flatMap(entry -> this.promote(entry, index))
            .switchIfEmpty(Mono.defer(() -> this.findForInteractionFrom(event, index + 1)));
    }

    /** Seeds a tier-{@code foundIndex} hit into every earlier tier, returning the hottest tier's canonical entry. */
    private @NotNull Mono<CachedResponse> promote(@NotNull CachedResponse entry, int foundIndex) {
        if (foundIndex == 0)
            return Mono.just(entry); // hot hit - nothing to promote

        Mono<Void> seedIntermediate = Flux.range(1, foundIndex - 1)
            .concatMap(i -> this.tiers.get(i).seed(entry))
            .then();

        return seedIntermediate.then(this.tiers.getFirst().seed(entry));
    }

}
