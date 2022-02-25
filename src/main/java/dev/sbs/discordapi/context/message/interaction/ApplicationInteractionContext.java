package dev.sbs.discordapi.context.message.interaction;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.util.DiscordResponseCache;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ApplicationInteractionContext<T extends InteractionCreateEvent> extends EventContext<T> {

    default void deferReply() {
        this.deferReply(false);
    }

    void deferReply(boolean ephemeral);

    Mono<Message> getReply();

    Mono<Void> interactionReply(InteractionApplicationCommandCallbackSpec interactionApplicationCommandCallbackSpec);

    @Override
    default Mono<Void> softReply(Response response) {
        return this.interactionReply(response.getD4jComponentCallbackSpec(this))
            .then(this.getReply())
            .onErrorMap(throwable -> SimplifiedException.wrapNative(throwable).build())
            .doOnError(throwable -> this.getDiscordBot().handleUncaughtException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    this,
                    throwable,
                    "Interaction Response Exception"
                )
            ))
            .flatMap(message -> Flux.fromIterable(response.getCurrentPage().getReactions())
                .flatMap(emoji -> message.addReaction(emoji.getD4jReaction()))
                .then(Mono.fromRunnable(() -> {
                    // Cache Message
                    DiscordResponseCache.Entry responseCacheEntry = this.getDiscordBot()
                        .getResponseCache()
                        .add(
                            message.getChannelId(),
                            this.getInteractUserId(),
                            message.getId(),
                            response
                        );

                    responseCacheEntry.updateLastInteract(); // Update TTL
                    responseCacheEntry.setUpdated();
                }))
            );
    }

}
