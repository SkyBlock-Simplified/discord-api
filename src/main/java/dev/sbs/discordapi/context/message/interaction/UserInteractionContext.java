package dev.sbs.discordapi.context.message.interaction;

import dev.sbs.discordapi.context.message.MessageContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.action.ActionComponent;
import dev.sbs.discordapi.util.DiscordResponseCache;
import discord4j.core.event.domain.Event;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public interface UserInteractionContext<T extends Event> extends MessageContext<T> {

    default void edit() {
        this.getResponse().ifPresent(this::edit);
    }

    default void edit(Response response) {
        this.getMessage()
            .flatMap(message -> message.edit(response.getD4jEditSpec())
                //.then(message.removeAllReactions())
                //.thenMany(Flux.fromIterable(response.getCurrentPage().getReactions()).flatMap(emoji -> message.addReaction(emoji.getD4jReaction()))) // Add Reactions
                .then(Mono.fromRunnable(() -> {
                    DiscordResponseCache.Entry responseCacheEntry = this.getResponseCacheEntry();
                    responseCacheEntry.updateResponse(response, true);
                    responseCacheEntry.setUpdated();
                }))
            )
            .block();
    }

    default @NotNull Optional<Response> getResponse() {
        return this.getDiscordBot().getResponseCache().getResponse(this.getResponseId());
    }

    default DiscordResponseCache.Entry getResponseCacheEntry() {
        return this.getDiscordBot().getResponseCache().getEntry(this.getResponseId()).orElse(null);
    }

    UUID getResponseId();

    default void edit(Function<Response.ResponseBuilder, Response.ResponseBuilder> responseBuilder) {
        this.getResponse().ifPresent(response -> this.edit(responseBuilder.apply(response.mutate()).build()));
    }

    default void modify(ActionComponent<?, ?> actionComponent) {
        this.getResponse().ifPresent(response -> this.getDiscordBot()
            .getResponseCache()
            .updateResponse(
                response.mutate()
                    .editComponent(actionComponent)
                    .build(),
                false
            )
        );
    }

}
