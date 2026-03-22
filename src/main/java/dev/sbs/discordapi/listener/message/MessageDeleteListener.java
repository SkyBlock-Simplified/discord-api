package dev.sbs.discordapi.listener.message;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.message.MessageDeleteEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Listener for message delete events, removing the corresponding {@link Followup}
 * from its parent {@link CachedResponse} when a tracked followup message is deleted.
 */
public class MessageDeleteListener extends DiscordListener<MessageDeleteEvent> {

    /**
     * Constructs a new {@code MessageDeleteListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public MessageDeleteListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public final Publisher<Void> apply(@NotNull MessageDeleteEvent event) {
        return Flux.fromIterable(this.getDiscordBot().getResponseHandler())
            .filter(entry -> entry.containsFollowup(event.getMessageId()))
            .flatMap(entry -> Mono.justOrEmpty(entry.findFollowup(event.getMessageId()))
                .doOnNext(followup -> entry.removeFollowup(followup.getIdentifier()))
            )
            .then();
    }

}
