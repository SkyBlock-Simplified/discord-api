package dev.sbs.discordapi.listener.message.reaction;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.context.reaction.ReactionContext;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.listener.DiscordListener;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.message.ReactionUserEmojiEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

public abstract class ReactionListener<E extends ReactionUserEmojiEvent> extends DiscordListener<E> {

    protected ReactionListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public Publisher<Void> apply(@NotNull E event) {
        return Mono.just(event)
            .filter(this::isBotMessage) // Only Bot Messages
            .filter(this::notBot) // Ignore Other Bots
            .thenMany(Flux.fromIterable(this.getDiscordBot().getResponseHandler()))
            .filter(entry -> entry.matchesMessage(event.getMessageId(), event.getUserId())) // Validate Message & User ID
            .singleOrEmpty()
            .flatMap(entry -> {
                final Emoji emoji = Emoji.of(event.getEmoji());

                return Flux.fromIterable(entry.getResponse().getHistoryHandler().getCurrentPage().getReactions())
                    .filter(reaction -> reaction.equals(emoji))
                    .singleOrEmpty()
                    .flatMap(reaction -> this.handleInteraction(event, entry, reaction, entry.findFollowup(event.getMessageId())))
                    .then(entry.updateLastInteract())
                    .then();
            });
    }

    protected abstract @NotNull ReactionContext getContext(@NotNull E event, @NotNull Response cachedMessage, @NotNull Emoji reaction, @NotNull Optional<Followup> followup);

    private Mono<Void> handleInteraction(@NotNull E event, @NotNull CachedResponse entry, @NotNull Emoji reaction, @NotNull Optional<Followup> followup) {
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
                .doOnNext(CachedResponse::setBusy)
                .then(reaction.getInteraction().apply(context).thenReturn(entry))
                .filter(CachedResponse::isModified)
                .flatMap(__ -> followup.isEmpty() ? context.edit() : context.editFollowup())
            );
    }

    private boolean notBot(@NotNull E event) {
        return !event.getUser().blockOptional().map(User::isBot).orElse(true);
    }

    private boolean isBotMessage(@NotNull E event) {
        return event.getMessage().blockOptional().flatMap(Message::getAuthor).map(User::isBot).orElse(false);
    }

}
