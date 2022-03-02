package dev.sbs.discordapi.context.message.interaction;

import dev.sbs.api.util.helper.FormatUtil;
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

    Mono<Message> getReply();

    Mono<Void> interactionReply(InteractionApplicationCommandCallbackSpec interactionApplicationCommandCallbackSpec);

    @Override
    default Mono<Void> reply(Response response) {
        return Flux.fromIterable(this.getDiscordBot().getResponseCache())
            .filter(entry -> entry.getResponse().getUniqueId().equals(this.getUniqueId()))
            .filter(entry -> entry.getResponse().isLoader())
            .singleOrEmpty()
            .flatMap(deferredReply -> {
                deferredReply.updateResponse(response);
                deferredReply.setUpdated();

                return this.getChannel()
                    .flatMap(channel -> channel.getMessageById(deferredReply.getMessageId()))
                    .flatMap(message -> message.edit(response.getD4jEditSpec()))
                    .flatMap(message -> Flux.fromIterable(response.getCurrentPage().getReactions())
                        .flatMap(emoji -> message.addReaction(emoji.getD4jReaction()))
                        .then(Mono.just(deferredReply))
                    );
            })
            .switchIfEmpty(
                this.interactionReply(response.getD4jComponentCallbackSpec(this))
                    .publishOn(response.getReactorScheduler())
                    .then(this.getReply())
                    .checkpoint(FormatUtil.format("Response Processing{0}", this.getIdentifier().map(identifier -> ": " + identifier).orElse("")))
                    .onErrorResume(throwable -> this.getDiscordBot().handleUncaughtException(
                        ExceptionContext.of(
                            this.getDiscordBot(),
                            this,
                            throwable,
                            "Response Exception"
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

                            if (!response.isLoader()) {
                                responseCacheEntry.updateLastInteract(); // Update TTL
                                responseCacheEntry.setUpdated();
                            }
                        }))
                    )
            )
            .then();
    }

}
