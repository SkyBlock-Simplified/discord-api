package dev.sbs.discordapi.listener.message.reaction;

import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.context.message.interaction.reaction.ReactionContext;
import dev.sbs.discordapi.listener.DiscordListener;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.util.DiscordResponseCache;
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
    public Publisher<Void> apply(E event) {
        return Flux.just(event)
            .filter(this::isBotMessage) // Only Bot Messages
            .filter(this::notBot) // Ignore Bots
            .flatMap(event_ -> Flux.fromIterable(this.getDiscordBot().getResponseCache()))
            .parallel()
            .filter(responseCacheEntry -> responseCacheEntry.getMessageId().equals(this.getMessageId(event))) // Validate Message ID
            .filter(responseCacheEntry -> responseCacheEntry.getUserId().equals(this.getUserId(event))) // Validate User ID
            .flatMap(responseCacheEntry -> {
                final Emoji emoji = this.getEmoji(event);

                return Flux.fromIterable(responseCacheEntry.getResponse().getCurrentPage().getReactions())
                    .filter(reaction -> reaction.equals(emoji))
                    .flatMap(reaction -> this.handleInteraction(event, responseCacheEntry, reaction));
            });
    }

    protected abstract ReactionContext getContext(E event, Response cachedMessage, Emoji reaction);

    protected abstract Snowflake getMessageId(E event);

    protected abstract Emoji getEmoji(E event);

    protected abstract Snowflake getUserId(E event);

    private Mono<Void> handleInteraction(E event, DiscordResponseCache.Entry responseCacheEntry, Emoji reaction) {
        return Mono.fromRunnable(() -> reaction.getInteraction().ifPresent(interaction -> {
            responseCacheEntry.setBusy();
            ReactionContext context = this.getContext(event, responseCacheEntry.getResponse(), reaction);

            try {
                interaction.accept(context);
            } catch (Exception uncaughtException) {
                this.getDiscordBot().handleUncaughtException(
                    ExceptionContext.of(
                        this.getDiscordBot(),
                        context,
                        uncaughtException,
                        FormatUtil.format("{0} Exception", this.getTitle())
                    )
                );
            }

            // Update Modified Cache Entries
            if (responseCacheEntry.isModified())
                context.edit();

            responseCacheEntry.updateLastInteract(); // Update TTL
        }));
    }

    protected abstract boolean isBot(E event);

    protected final boolean notBot(E event) {
        return !this.isBot(event);
    }

    protected abstract boolean isBotMessage(E event);

}
