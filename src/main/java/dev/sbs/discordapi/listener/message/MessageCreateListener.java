package dev.sbs.discordapi.listener.message;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.MessageContext;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.message.MessageCreateEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

public class MessageCreateListener extends DiscordListener<MessageCreateEvent> {

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
                .getInteraction()
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
