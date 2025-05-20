package dev.sbs.discordapi.context.reaction;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.MessageContext;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.event.domain.message.ReactionRemoveEvent;
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

public interface ReactionContext extends MessageContext<MessageEvent> {

    @NotNull Emoji getEmoji();

    @NotNull Type getType();

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

    static @NotNull ReactionContext ofAdd(@NotNull DiscordBot discordBot, @NotNull ReactionAddEvent event, @NotNull Response cachedMessage, @NotNull Emoji emoji, @NotNull Optional<Followup> followup) {
        return new AddImpl(
            discordBot,
            event,
            cachedMessage.getUniqueId(),
            emoji,
            followup
        );
    }

    static @NotNull ReactionContext ofRemove(@NotNull DiscordBot discordBot, @NotNull ReactionRemoveEvent event, @NotNull Response cachedMessage, @NotNull Emoji emoji, @NotNull Optional<Followup> followup) {
        return new RemoveImpl(
            discordBot,
            event,
            cachedMessage.getUniqueId(),
            emoji,
            followup
        );
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class AddImpl implements ReactionContext {

        private final @NotNull DiscordBot discordBot;
        private final @NotNull ReactionAddEvent event;
        private final @NotNull UUID responseId;
        private final @NotNull Emoji emoji;
        private final @NotNull Type type = Type.ADD;
        private final @NotNull Optional<Followup> followup;

        @Override
        public @NotNull Snowflake getChannelId() {
            return this.getEvent().getChannelId();
        }

        @Override
        public Mono<Guild> getGuild() {
            return this.getEvent().getGuild();
        }

        @Override
        public Optional<Snowflake> getGuildId() {
            return this.getEvent().getGuildId();
        }

        @Override
        public @NotNull User getInteractUser() {
            return Objects.requireNonNull(this.getEvent().getUser().block());
        }

        @Override
        public @NotNull Snowflake getInteractUserId() {
            return this.getEvent().getUserId();
        }

        @Override
        public Mono<Message> getMessage() {
            return this.getEvent().getMessage();
        }

        @Override
        public Snowflake getMessageId() {
            return this.getEvent().getMessageId();
        }

    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class RemoveImpl implements ReactionContext {

        private final @NotNull DiscordBot discordBot;
        private final @NotNull ReactionRemoveEvent event;
        private final @NotNull UUID responseId;
        private final @NotNull Emoji emoji;
        private final @NotNull Type type = Type.REMOVE;
        private final @NotNull Optional<Followup> followup;

        @Override
        public @NotNull Snowflake getChannelId() {
            return this.getEvent().getChannelId();
        }

        @Override
        public Mono<Guild> getGuild() {
            return this.getEvent().getGuild();
        }

        @Override
        public Optional<Snowflake> getGuildId() {
            return this.getEvent().getGuildId();
        }

        @Override
        public @NotNull User getInteractUser() {
            return Objects.requireNonNull(this.getEvent().getUser().block());
        }

        @Override
        public @NotNull Snowflake getInteractUserId() {
            return this.getEvent().getUserId();
        }

        @Override
        public Mono<Message> getMessage() {
            return this.getEvent().getMessage();
        }

        @Override
        public Snowflake getMessageId() {
            return this.getEvent().getMessageId();
        }

    }

    enum Type {

        ADD,
        REMOVE

    }

}
