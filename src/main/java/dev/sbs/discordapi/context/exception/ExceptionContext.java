package dev.sbs.discordapi.context.exception;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.context.deferrable.command.CommandContext;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.Event;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

public interface ExceptionContext<T extends Event> extends EventContext<T> {

    @Override
    default Mono<MessageChannel> getChannel() {
        return this.getEventContext().getChannel();
    }

    default @NotNull Snowflake getChannelId() {
        return this.getEventContext().getChannelId();
    }

    @Override
    default @NotNull T getEvent() {
        return this.getEventContext().getEvent();
    }

    @NotNull EventContext<T> getEventContext();

    @NotNull Throwable getException();

    @Override
    default Mono<Guild> getGuild() {
        return this.getEventContext().getGuild();
    }

    @Override
    default Optional<Snowflake> getGuildId() {
        return this.getEventContext().getGuildId();
    }

    @Override
    default @NotNull User getInteractUser() {
        return this.getEventContext().getInteractUser();
    }

    @Override
    default @NotNull Snowflake getInteractUserId() {
        return this.getEventContext().getInteractUserId();
    }

    @Override
    default Mono<Void> reply(@NotNull Response response) {
        return this.getEventContext().reply(response);
    }

    @NotNull String getTitle();

    static @NotNull ExceptionContext<?> of(@NotNull DiscordBot discordBot, @NotNull CommandContext<?> context, @NotNull Throwable throwable) {
        return of(
            discordBot,
            context,
            throwable,
            "Command Exception"
        );
    }

    static <T extends Event> @NotNull ExceptionContext<T> of(@NotNull DiscordBot discordBot, @NotNull EventContext<T> context, @NotNull Throwable throwable, @NotNull String title) {
        return new Impl<>(discordBot, context, throwable, title);
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl<T extends Event> implements ExceptionContext<T> {

        private final @NotNull DiscordBot discordBot;
        private final @NotNull EventContext<T> eventContext;
        private final @NotNull UUID responseId = UUID.randomUUID();
        private final @NotNull Throwable exception;
        private final @NotNull String title;

    }

}
