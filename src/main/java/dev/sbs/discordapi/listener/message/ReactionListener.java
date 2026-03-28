package dev.sbs.discordapi.listener.message;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.capability.ExceptionContext;
import dev.sbs.discordapi.context.message.ReactionContext;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.handler.response.ResponseFollowup;
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

/**
 * Abstract base for reaction event listeners, providing the shared flow of
 * validating that the reaction targets a bot message from a non-bot user,
 * matching it against a {@link CachedResponse}'s registered reactions, and
 * dispatching to the reaction's interaction handler.
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

    /**
     * Creates the typed context for the given reaction interaction.
     *
     * @param event the Discord4J reaction event
     * @param cachedMessage the cached response the reaction belongs to
     * @param reaction the matched emoji
     * @param followup the matched followup, if the reaction targets one
     * @return the constructed context
     */
    protected abstract @NotNull ReactionContext getContext(@NotNull E event, @NotNull Response cachedMessage, @NotNull Emoji reaction, @NotNull Optional<ResponseFollowup> followup);

    /**
     * Executes the reaction's registered interaction handler within an error-handling
     * pipeline, then edits the response if it was modified.
     *
     * @param event the Discord4J reaction event
     * @param entry the matched response cache entry
     * @param reaction the matched emoji
     * @param followup the matched followup, if the reaction targets one
     * @return a reactive pipeline completing when the interaction is handled
     */
    private Mono<Void> handleInteraction(@NotNull E event, @NotNull CachedResponse entry, @NotNull Emoji reaction, @NotNull Optional<ResponseFollowup> followup) {
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

    /**
     * Returns {@code true} if the reacting user is not a bot.
     *
     * @param event the reaction event
     * @return {@code true} if the user is not a bot
     */
    private boolean notBot(@NotNull E event) {
        return !event.getUser().blockOptional().map(User::isBot).orElse(true);
    }

    /**
     * Returns {@code true} if the message that was reacted to was authored by a bot.
     *
     * @param event the reaction event
     * @return {@code true} if the message author is a bot
     */
    private boolean isBotMessage(@NotNull E event) {
        return event.getMessage().blockOptional().flatMap(Message::getAuthor).map(User::isBot).orElse(false);
    }

}
