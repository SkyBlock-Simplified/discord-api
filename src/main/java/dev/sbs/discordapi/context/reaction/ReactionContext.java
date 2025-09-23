package dev.sbs.discordapi.context.reaction;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.MessageContext;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.ReactionUserEmojiEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public interface ReactionContext extends MessageContext<ReactionUserEmojiEvent> {

    @NotNull Emoji getEmoji();

    @NotNull ReactionContext.Type getType();

    @Override
    default @NotNull Snowflake getChannelId() {
        return this.getEvent().getChannelId();
    }

    @Override
    default Mono<Guild> getGuild() {
        return this.getEvent().getGuild();
    }

    @Override
    default Optional<Snowflake> getGuildId() {
        return this.getEvent().getGuildId();
    }

    @Override
    default @NotNull User getInteractUser() {
        return Objects.requireNonNull(this.getEvent().getUser().block());
    }

    @Override
    default @NotNull Snowflake getInteractUserId() {
        return this.getEvent().getUserId();
    }

    @Override
    default Mono<Message> getMessage() {
        return this.getEvent().getMessage();
    }

    @Override
    default Snowflake getMessageId() {
        return this.getEvent().getMessageId();
    }

    default boolean isSuperReaction() {
        return this.getEvent().isSuperReaction();
    }

    default Mono<Void> removeReactions() {
        return this.getMessage()
            .flatMap(message -> message.removeReactions(this.getEmoji().getD4jReaction()))
            .then(this.withResponseEntry(entry -> entry.updateResponse(
                entry.getResponse()
                    .mutate()
                    .editPage(
                        entry.getResponse()
                            .getHistoryHandler()
                            .getCurrentPage()
                            .mutate()
                            .clearReactions()
                            .build()
                    )
                    .build()
            )));
    }

    default Mono<Void> removeReaction() {
        return this.getMessage()
            .flatMap(message -> message.removeReactions(this.getEmoji().getD4jReaction()))
            .then(this.withResponseEntry(entry -> entry.updateResponse(
                entry.getResponse()
                    .mutate()
                    .editPage(
                        entry.getResponse()
                            .getHistoryHandler()
                            .getCurrentPage()
                            .mutate()
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
            .then(this.withResponseEntry(entry -> entry.updateResponse(
                entry.getResponse()
                    .mutate()
                    .editPage(
                        entry.getResponse()
                            .getHistoryHandler()
                            .getCurrentPage()
                            .mutate()
                            .clearReaction(this.getEmoji())
                            .build()
                    )
                    .build()
            )));
    }

    static @NotNull ReactionContext of(
        @NotNull DiscordBot discordBot,
        @NotNull ReactionUserEmojiEvent event,
        @NotNull Response cachedMessage,
        @NotNull Emoji emoji,
        @NotNull Type type,
        @NotNull Optional<Followup> followup
    ) {
        return new Impl(
            discordBot,
            event,
            cachedMessage.getUniqueId(),
            emoji,
            type,
            followup
        );
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements ReactionContext {

        private final @NotNull DiscordBot discordBot;
        private final @NotNull ReactionUserEmojiEvent event;
        private final @NotNull UUID responseId;
        private final @NotNull Emoji emoji;
        private final @NotNull Type type;
        private final @NotNull Optional<Followup> followup;

    }

    enum Type {

        ADD,
        REMOVE

    }

}
