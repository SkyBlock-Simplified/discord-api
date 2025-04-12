package dev.sbs.discordapi.listener.message.reaction;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.context.reaction.ReactionContext;
import dev.sbs.discordapi.listener.DiscordListener;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

public abstract class ReactionListener<E extends MessageEvent> extends DiscordListener<E> {

    protected ReactionListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public Publisher<Void> apply(@NotNull E event) {
        return Mono.just(event)
            .filter(this::isBotMessage) // Only Bot Messages
            .filter(this::notBot) // Ignore Other Bots
            .thenMany(Flux.fromIterable(this.getDiscordBot().getResponseCache()))
            .filter(entry -> entry.matchesMessage(this.getMessageId(event), this.getUserId(event))) // Validate Message & User ID
            .singleOrEmpty()
            .flatMap(entry -> {
                final Emoji emoji = this.getEmoji(event);

                return Flux.fromIterable(entry.getResponse().getHistoryHandler().getCurrentPage().getReactions())
                    .filter(reaction -> reaction.equals(emoji))
                    .singleOrEmpty()
                    .flatMap(reaction -> this.handleInteraction(event, entry, reaction, entry.findFollowup(this.getMessageId(event))))
                    .then(entry.updateLastInteract())
                    .then();
            });
    }

    protected abstract @NotNull ReactionContext getContext(@NotNull E event, @NotNull Response cachedMessage, @NotNull Emoji reaction, @NotNull Optional<Response.Cache.Followup> followup);

    protected abstract @NotNull Snowflake getMessageId(@NotNull E event);

    protected abstract @NotNull Emoji getEmoji(@NotNull E event);

    protected abstract @NotNull Snowflake getUserId(@NotNull E event);

    private Mono<Void> handleInteraction(@NotNull E event, @NotNull Response.Cache.Entry entry, @NotNull Emoji reaction, @NotNull Optional<Response.Cache.Followup> followup) {
        return Mono.just(this.getContext(event, entry.getResponse(), reaction, followup))
            .flatMap(context -> Mono.just(entry)
                .onErrorResume(throwable -> this.getDiscordBot().getExceptionHandler().handleException(
                    ExceptionContext.of(
                        this.getDiscordBot(),
                        context,
                        throwable,
                        String.format("%s Exception", this.getTitle())
                    )
                ))
                .doOnNext(Response.Cache.Entry::setBusy)
                .then(reaction.getInteraction().apply(context).thenReturn(entry))
                .filter(Response.Cache.Entry::isModified)
                .flatMap(__ -> followup.isEmpty() ? context.edit() : context.editFollowup())
            );
    }

    protected abstract boolean isBot(@NotNull E event);

    protected final boolean notBot(@NotNull E event) {
        return !this.isBot(event);
    }

    protected abstract boolean isBotMessage(@NotNull E event);

}
