package dev.sbs.discordapi.context.message.interaction;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.context.message.MessageContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.action.ActionComponent;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.util.cache.DiscordResponseCache;
import discord4j.core.event.domain.Event;
import discord4j.core.object.entity.Message;
import discord4j.core.object.reaction.Reaction;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public interface UserInteractionContext<T extends Event> extends MessageContext<T> {

    default Mono<Void> edit() {
        return Mono.justOrEmpty(this.getResponse()).flatMap(this::edit);
    }

    default Mono<Void> edit(Response response) {
        return this.editMessage(response)
            .onErrorResume(throwable -> this.getDiscordBot().handleUncaughtException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    this,
                    throwable,
                    "User Interaction Exception"
                )
            ))
            .flatMap(message -> {
                // Update Reactions
                ConcurrentList<Emoji> newReactions = response.getCurrentPage().getReactions();

                // Current Reactions
                ConcurrentList<Emoji> currentReactions = message.getReactions()
                    .stream()
                    .filter(Reaction::selfReacted)
                    .map(Reaction::getEmoji)
                    .map(Emoji::of)
                    .collect(Concurrent.toList());

                Mono<Void> mono = Mono.empty();

                // Remove Existing Reactions
                if (currentReactions.stream().anyMatch(messageEmoji -> !newReactions.contains(messageEmoji)))
                    mono = message.removeAllReactions();

                return mono.then(Mono.when(
                    newReactions.stream()
                        .map(emoji -> message.addReaction(emoji.getD4jReaction()))
                        .collect(Concurrent.toList())
                ));
            })
            .then(Mono.fromRunnable(() -> {
                DiscordResponseCache.Entry responseCacheEntry = this.getResponseCacheEntry();
                responseCacheEntry.updateResponse(response, true);
                responseCacheEntry.setUpdated();
            }));
    }

    default Mono<Message> editMessage(Response response) {
        return this.getMessage().flatMap(message -> message.edit(response.getD4jEditSpec()));
    }

    default @NotNull Optional<Response> getResponse() {
        return this.getDiscordBot().getResponseCache().getResponse(this.getResponseId());
    }

    default DiscordResponseCache.Entry getResponseCacheEntry() {
        return this.getDiscordBot().getResponseCache().getEntry(this.getResponseId()).orElse(null);
    }

    UUID getResponseId();

    default Mono<Void> edit(Function<Page.PageBuilder, Page.PageBuilder> currentPage) {
        return Mono.justOrEmpty(this.getResponse()).flatMap(response -> this.edit(
            response.mutate()
                .editPage(currentPage.apply(response.getCurrentPage().mutate()).build())
                .build()
        ));
    }

    default void modify(ActionComponent<?, ?> actionComponent) {
        this.getResponse().ifPresent(response -> this.getDiscordBot()
            .getResponseCache()
            .updateResponse(
                response.mutate()
                    .editPage(
                        response.getCurrentPage()
                            .mutate()
                            .editComponent(actionComponent)
                            .build()
                    )
                    .build(),
                false
            )
        );
    }

}
