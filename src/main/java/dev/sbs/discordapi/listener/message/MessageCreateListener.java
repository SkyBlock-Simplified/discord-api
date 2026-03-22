package dev.sbs.discordapi.listener.message;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.message.MessageContext;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.message.MessageCreateEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

/**
 * Listener for message create events from bot users, matching the message to a
 * {@link CachedResponse} and invoking its registered create interaction handler.
 */
public class MessageCreateListener extends DiscordListener<MessageCreateEvent> {

    /**
     * Constructs a new {@code MessageCreateListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public MessageCreateListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public final Publisher<Void> apply(@NotNull MessageCreateEvent event) {
        return Flux.fromIterable(this.getDiscordBot().getResponseHandler())
            .filter(entry -> event.getMessage().getUserData().bot().toOptional().orElse(false)) // Only Bots
            .filter(entry -> entry.matchesMessage(entry.getMessageId(), event.getMessage().getUserData().id())) // Validate Message & User ID
            .singleOrEmpty()
            .doOnNext(CachedResponse::setBusy)
            .flatMap(entry -> entry.getResponse()
                .getCreateInteraction()
                .apply(MessageContext.ofCreate(
                    this.getDiscordBot(),
                    event,
                    entry.getResponse(),
                    entry.findFollowup(event.getMessage().getId())
                ))
                .then(entry.updateLastInteract())
                .then()
            );
    }

}
