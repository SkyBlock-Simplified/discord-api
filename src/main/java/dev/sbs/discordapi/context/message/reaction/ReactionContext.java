package dev.sbs.discordapi.context.message.reaction;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.ResponseContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.util.cache.ResponseCache;
import discord4j.core.event.domain.message.MessageEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.event.domain.message.ReactionRemoveEvent;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;

public interface ReactionContext extends ResponseContext<MessageEvent> {

    @Override
    default Mono<Message> discordBuildFollowup(@NotNull Response response) {
        return this.discordBuildMessage(
            response.mutate()
                .withReference(this.getMessageId())
                .build()
        );
    }

    @Override
    default Mono<Void> discordDeleteFollowup(@NotNull String identifier) {
        return Mono.justOrEmpty(this.getFollowup(identifier))
            .flatMap(followup -> this.getChannel().flatMap(channel -> channel.getMessageById(followup.getMessageId())))
            .flatMap(Message::delete);
    }

    @Override
    default Mono<Message> discordEditFollowup(@NotNull String identifier, @NotNull Response response) {
        return Mono.justOrEmpty(this.getFollowup(identifier))
            .flatMap(followup -> this.discordEditMessage(
                followup.getMessageId(),
                response.mutate()
                    .withReference(this.getMessageId())
                    .build()
            ))
            .publishOn(response.getReactorScheduler());
    }

    @NotNull Emoji getEmoji();

    @NotNull Type getType();

    default Mono<Void> removeReactions() {
        return this.getMessage()
            .flatMap(message -> message.removeReactions(this.getEmoji().getD4jReaction()))
            .then(this.withResponseFunction(entry -> entry.updateResponse(
                entry.getResponse()
                    .mutate()
                    .editPage(
                        entry.getResponse()
                            .getHistoryHandler()
                            .getCurrentPage()
                            .mutate()
                            .clearComponents()
                            .clearReactions()
                            .build()
                    )
                    .build()
            )));
    }

    default Mono<Void> removeReaction() {
        return this.getMessage()
            .flatMap(message -> message.removeReactions(this.getEmoji().getD4jReaction()))
            .then(this.withResponseFunction(entry -> entry.updateResponse(
                entry.getResponse()
                    .mutate()
                    .editPage(
                        entry.getResponse()
                            .getHistoryHandler()
                            .getCurrentPage()
                            .mutate()
                            .clearComponents()
                            .clearReaction(this.getEmoji())
                            .build()
                    )
                    .build()
            )));
    }

    default Mono<Void> removeUserReaction() {
        return this.getMessage().flatMap(message -> message.removeReaction(this.getEmoji().getD4jReaction(), this.getInteractUserId()));
    }

    default Mono<Void> removeSelfReaction() {
        return this.getMessage()
            .flatMap(message -> message.removeSelfReaction(this.getEmoji().getD4jReaction()))
            .then(this.withResponseFunction(entry -> entry.updateResponse(
                entry.getResponse()
                    .mutate()
                    .editPage(
                        entry.getResponse()
                            .getHistoryHandler()
                            .getCurrentPage()
                            .mutate()
                            .clearComponents()
                            .clearReaction(this.getEmoji())
                            .build()
                    )
                    .build()
            )));
    }

    static ReactionContext ofAdd(@NotNull DiscordBot discordBot, @NotNull ReactionAddEvent event, @NotNull Response cachedMessage, @NotNull Emoji emoji, @NotNull Optional<ResponseCache.Followup> followup) {
        return new ReactionAddContextImpl(discordBot, event, cachedMessage.getUniqueId(), emoji, followup);
    }

    static ReactionContext ofRemove(@NotNull DiscordBot discordBot, @NotNull ReactionRemoveEvent event, @NotNull Response cachedMessage, @NotNull Emoji emoji, @NotNull Optional<ResponseCache.Followup> followup) {
        return new ReactionRemoveContextImpl(discordBot, event, cachedMessage.getUniqueId(), emoji, followup);
    }

    enum Type {

        ADD,
        REMOVE

    }

}
