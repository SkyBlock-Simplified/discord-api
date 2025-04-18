package dev.sbs.discordapi.context.exception;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.context.deferrable.command.CommandContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.embed.Embed;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.Event;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public interface ExceptionContext<T extends Event> extends EventContext<T> {

    @Override
    default Mono<MessageChannel> getChannel() {
        return this.getEventContext().getChannel();
    }

    default @NotNull Snowflake getChannelId() {
        return this.getEventContext().getChannelId();
    }

    Optional<Consumer<Embed.Builder>> getEmbedBuilderConsumer();

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

    static @NotNull ExceptionContext<?> of(@NotNull DiscordBot discordBot, @NotNull CommandContext<?> commandContext, @NotNull Throwable throwable) {
        return of(
            discordBot,
            commandContext,
            throwable,
            "Command Exception",
            embedBuilder -> embedBuilder.withTitle("Command :: %s", commandContext.getCommand().getStructure().name())
        );
    }

    static <T extends Event> @NotNull ExceptionContext<T> of(@NotNull DiscordBot discordBot, @NotNull EventContext<T> eventContext, @NotNull Throwable throwable, @NotNull String title) {
        return of(discordBot, eventContext, throwable, title, Optional.empty());
    }

    static <T extends Event> @NotNull ExceptionContext<T> of(@NotNull DiscordBot discordBot, @NotNull EventContext<T> eventContext, @NotNull Throwable throwable, @NotNull String title, @Nullable Consumer<Embed.Builder> embedBuilderConsumer) {
        return of(discordBot, eventContext, throwable, title, Optional.ofNullable(embedBuilderConsumer));
    }

    static <T extends Event> @NotNull ExceptionContext<T> of(@NotNull DiscordBot discordBot, @NotNull EventContext<T> eventContext, @NotNull Throwable throwable, @NotNull String title, Optional<Consumer<Embed.Builder>> embedBuilderConsumer) {
        return new Impl<>(discordBot, eventContext, throwable, title, embedBuilderConsumer);
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl<T extends Event> implements ExceptionContext<T> {

        private final @NotNull DiscordBot discordBot;
        private final @NotNull EventContext<T> eventContext;
        private final @NotNull UUID responseId = UUID.randomUUID();
        private final @NotNull Throwable exception;
        private final @NotNull String title;
        private final @NotNull Optional<Consumer<Embed.Builder>> embedBuilderConsumer;

    }

}
