package dev.simplified.discordapi.listener.message;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.context.capability.ExceptionContext;
import dev.simplified.discordapi.context.message.ReactionContext;
import dev.simplified.discordapi.handler.response.CachedResponse;
import dev.simplified.discordapi.listener.DiscordListener;
import dev.simplified.discordapi.response.Emoji;
import dev.simplified.discordapi.response.Response;
import discord4j.core.event.domain.message.ReactionUserEmojiEvent;
import discord4j.core.object.entity.User;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Abstract base for reaction event listeners, providing the shared flow of
 * validating that the reaction targets a bot message from a non-bot user,
 * matching it against a {@link CachedResponse}'s registered reactions via the
 * response locator, and dispatching to the reaction's interaction handler.
 * <p>
 * Concrete subclasses ({@link ReactionAddListener}, {@link ReactionRemoveListener})
 * supply the {@link ReactionContext} with the appropriate {@link ReactionContext.Type}.
 *
 * @param <E> the Discord4J reaction event type
 */
public abstract class ReactionListener<E extends ReactionUserEmojiEvent> extends DiscordListener<E> {

    /**
     * Constructs a new {@code ReactionListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    protected ReactionListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public Publisher<Void> apply(@NotNull E event) {
        return event.getMessage()
            .filter(message -> message.getAuthor().map(User::isBot).orElse(false))
            .flatMap(ignored -> event.getUser())
            .filter(user -> !user.isBot())
            .flatMap(ignored -> this.getDiscordBot().getResponseLocator().findByMessage(event.getMessageId()))
            .filter(entry -> entry.getUserId().equals(event.getUserId()))
            .flatMap(entry -> {
                final Emoji emoji = Emoji.of(event.getEmoji());
                Optional<CachedResponse> followup = entry.isFollowup() ? Optional.of(entry) : Optional.empty();

                return Flux.fromIterable(entry.getResponse().getHistoryHandler().getCurrentPage().getReactions())
                    .filter(reaction -> reaction.equals(emoji))
                    .singleOrEmpty()
                    .flatMap(reaction -> this.handleInteraction(event, entry, reaction, followup))
                    .then(entry.finalizeInteraction())
                    .then();
            });
    }

    /**
     * Creates the typed context for the given reaction interaction.
     *
     * @param event the Discord4J reaction event
     * @param cachedMessage the cached response the reaction belongs to
     * @param reaction the matched emoji
     * @param followup the matched followup, if the reaction targets one
     * @return the constructed context
     */
    protected abstract @NotNull ReactionContext getContext(@NotNull E event, @NotNull Response cachedMessage, @NotNull Emoji reaction, @NotNull Optional<CachedResponse> followup);

    /**
     * Executes the reaction's registered interaction handler within an error-handling
     * pipeline, then edits the response if it was modified.
     */
    private Mono<Void> handleInteraction(@NotNull E event, @NotNull CachedResponse entry, @NotNull Emoji reaction, @NotNull Optional<CachedResponse> followup) {
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

}
