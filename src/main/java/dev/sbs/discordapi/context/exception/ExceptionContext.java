package dev.sbs.discordapi.context.exception;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.embed.Embed;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.Event;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Consumer;

public interface ExceptionContext<T extends Event> extends EventContext<T> {

    @Override
    default Mono<MessageChannel> getChannel() {
        return this.getEventContext().getChannel();
    }

    default Snowflake getChannelId() {
        return this.getEventContext().getChannelId();
    }

    Optional<Consumer<Embed.EmbedBuilder>> getEmbedBuilderConsumer();

    @Override
    default T getEvent() {
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
    default Mono<User> getInteractUser() {
        return this.getEventContext().getInteractUser();
    }

    @Override
    default Snowflake getInteractUserId() {
        return this.getEventContext().getInteractUserId();
    }

    @Override
    default Mono<Void> reply(Response response) {
        return this.getEventContext().reply(response);
    }

    @NotNull String getTitle();

    static <T extends Event> ExceptionContext<T> of(@NotNull DiscordBot discordBot, @NotNull EventContext<T> eventContext, @NotNull Throwable throwable, @NotNull String title) {
        return of(discordBot, eventContext, throwable, title, Optional.empty());
    }

    static <T extends Event> ExceptionContext<T> of(@NotNull DiscordBot discordBot, @NotNull EventContext<T> eventContext, @NotNull Throwable throwable, @NotNull String title, @Nullable Consumer<Embed.EmbedBuilder> embedBuilderConsumer) {
        return of(discordBot, eventContext, throwable, title, Optional.ofNullable(embedBuilderConsumer));
    }

    static <T extends Event> ExceptionContext<T> of(@NotNull DiscordBot discordBot, @NotNull EventContext<T> eventContext, @NotNull Throwable throwable, @NotNull String title, Optional<Consumer<Embed.EmbedBuilder>> embedBuilderConsumer) {
        return new ExceptionContextImpl<>(discordBot, eventContext, throwable, title, embedBuilderConsumer);
    }

}
