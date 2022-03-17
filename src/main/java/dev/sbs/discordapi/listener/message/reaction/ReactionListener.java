package dev.sbs.discordapi.listener.message.reaction;

import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.context.message.interaction.reaction.ReactionContext;
import dev.sbs.discordapi.listener.DiscordListener;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.util.cache.DiscordResponseCache;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageEvent;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class ReactionListener<E extends MessageEvent> extends DiscordListener<E> {

    protected ReactionListener(DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public Publisher<Void> apply(E reactionEvent) {
        return Mono.just(reactionEvent)
            .filter(this::isBotMessage) // Only Bot Messages
            .filter(this::notBot) // Ignore Bots
            .flatMap(event -> Flux.fromIterable(this.getDiscordBot().getResponseCache())
                .parallel()
                .filter(responseCacheEntry -> responseCacheEntry.getMessageId().equals(this.getMessageId(event))) // Validate Message ID
                .filter(responseCacheEntry -> responseCacheEntry.getUserId().equals(this.getUserId(event))) // Validate User ID
                .flatMap(responseCacheEntry -> {
                    final Emoji emoji = this.getEmoji(event);

                    return Flux.fromIterable(responseCacheEntry.getResponse().getCurrentPage().getReactions())
                        .filter(reaction -> reaction.equals(emoji))
                        .flatMap(reaction -> this.handleInteraction(event, responseCacheEntry, reaction));
                })
                .then()
            );
    }

    protected abstract ReactionContext getContext(E event, Response cachedMessage, Emoji reaction);

    protected abstract Snowflake getMessageId(E event);

    protected abstract Emoji getEmoji(E event);

    protected abstract Snowflake getUserId(E event);

    private Mono<Void> handleInteraction(E event, DiscordResponseCache.Entry responseCacheEntry, Emoji reaction) {
        return Mono.just(this.getContext(event, responseCacheEntry.getResponse(), reaction))
            .flatMap(context -> Mono.just(responseCacheEntry)
                .onErrorResume(throwable -> this.getDiscordBot().handleUncaughtException(
                    ExceptionContext.of(
                        this.getDiscordBot(),
                        context,
                        throwable,
                        FormatUtil.format("{0} Exception", this.getTitle())
                    )
                ))
                .doOnNext(DiscordResponseCache.Entry::setBusy)
                .flatMap(entry -> reaction.getInteraction().apply(context))
                .flatMap(__ -> Mono.just(responseCacheEntry))
                .doOnNext(DiscordResponseCache.Entry::updateLastInteract)
                .filter(DiscordResponseCache.Entry::isModified)
                .flatMap(entry -> context.edit())
            ).then();
    }

    protected abstract boolean isBot(E event);

    protected final boolean notBot(E event) {
        return !this.isBot(event);
    }

    protected abstract boolean isBotMessage(E event);

}
