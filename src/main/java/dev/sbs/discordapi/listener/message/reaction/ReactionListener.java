package dev.sbs.discordapi.listener.message.reaction;

import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.context.message.reaction.ReactionContext;
import dev.sbs.discordapi.listener.DiscordListener;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.util.cache.ResponseCache;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class ReactionListener<E extends MessageEvent> extends DiscordListener<E> {

    protected ReactionListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public Publisher<Void> apply(@NotNull E reactionEvent) {
        return Mono.just(reactionEvent)
            .filter(this::isBotMessage) // Only Bot Messages
            .filter(this::notBot) // Ignore Bots
            .flatMap(event -> Flux.fromIterable(this.getDiscordBot().getResponseCache())
                .parallel()
                .filter(responseCacheEntry -> responseCacheEntry.getMessageId().equals(this.getMessageId(event))) // Validate Message ID
                .filter(responseCacheEntry -> responseCacheEntry.getUserId().equals(this.getUserId(event))) // Validate User ID
                .flatMap(responseCacheEntry -> {
                    final Emoji emoji = this.getEmoji(event);

                    return Flux.fromIterable(responseCacheEntry.getResponse().getHistoryHandler().getCurrentPage().getReactions())
                        .filter(reaction -> reaction.equals(emoji))
                        .flatMap(reaction -> this.handleInteraction(event, responseCacheEntry, reaction));
                })
                .then()
            );
    }

    protected abstract ReactionContext getContext(@NotNull E event, @NotNull Response cachedMessage, @NotNull Emoji reaction);

    protected abstract Snowflake getMessageId(@NotNull E event);

    protected abstract Emoji getEmoji(@NotNull E event);

    protected abstract Snowflake getUserId(@NotNull E event);

    private Mono<Void> handleInteraction(@NotNull E event, @NotNull ResponseCache.Entry responseCacheEntry, @NotNull Emoji reaction) {
        return Mono.just(this.getContext(event, responseCacheEntry.getResponse(), reaction))
            .flatMap(context -> Mono.just(responseCacheEntry)
                .onErrorResume(throwable -> this.getDiscordBot().handleException(
                    ExceptionContext.of(
                        this.getDiscordBot(),
                        context,
                        throwable,
                        FormatUtil.format("{0} Exception", this.getTitle())
                    )
                ))
                .doOnNext(ResponseCache.Entry::setBusy)
                .flatMap(entry -> reaction.getInteraction().apply(context))
                .flatMap(__ -> Mono.just(responseCacheEntry))
                .doOnNext(ResponseCache.Entry::updateLastInteract)
                .filter(ResponseCache.Entry::isModified)
                .flatMap(entry -> context.edit())
            ).then();
    }

    protected abstract boolean isBot(@NotNull E event);

    protected final boolean notBot(@NotNull E event) {
        return !this.isBot(event);
    }

    protected abstract boolean isBotMessage(@NotNull E event);

}
