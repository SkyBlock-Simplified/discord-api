package dev.simplified.discordapi.listener.message;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.context.scope.MessageContext;
import dev.simplified.discordapi.handler.response.CachedResponse;
import dev.simplified.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.message.MessageCreateEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Listener for message create events from bot users, matching the message to a
 * {@link CachedResponse} via the response locator and invoking its registered
 * create interaction handler.
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
        if (!event.getMessage().getUserData().bot().toOptional().orElse(false))
            return Mono.empty();

        return this.getDiscordBot()
            .getResponseLocator()
            .findByMessage(event.getMessage().getId())
            .flatMap(entry -> this.dispatchCreate(event, entry));
    }

    private @NotNull Mono<Void> dispatchCreate(@NotNull MessageCreateEvent event, @NotNull CachedResponse entry) {
        entry.setBusy();
        Optional<CachedResponse> followup = entry.isFollowup() ? Optional.of(entry) : Optional.empty();
        return entry.getResponse()
            .getCreateInteraction()
            .apply(MessageContext.ofCreate(
                this.getDiscordBot(),
                event,
                entry.getResponse(),
                followup
            ))
            .then(entry.finalizeInteraction())
            .then();
    }

}
