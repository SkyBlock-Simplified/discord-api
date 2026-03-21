package dev.sbs.discordapi.context.exception;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.context.deferrable.command.CommandContext;
import dev.sbs.discordapi.handler.exception.ExceptionHandler;
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

/**
 * Decorator around an existing {@link EventContext} that augments it with exception
 * information, used by the {@link ExceptionHandler} to report and log errors that occur
 * during event processing.
 *
 * <p>
 * All {@link EventContext} methods delegate to the wrapped {@link #getEventContext()
 * eventContext}, while {@link #getException()} and {@link #getTitle()} provide the
 * error-specific metadata.
 *
 * @param <T> the Discord4J {@link Event} type of the wrapped context
 * @see ExceptionHandler
 */
public interface ExceptionContext<T extends Event> extends EventContext<T> {

    /** {@inheritDoc} */
    @Override
    default Mono<MessageChannel> getChannel() {
        return this.getEventContext().getChannel();
    }

    /** {@inheritDoc} */
    default @NotNull Snowflake getChannelId() {
        return this.getEventContext().getChannelId();
    }

    /** {@inheritDoc} */
    @Override
    default @NotNull T getEvent() {
        return this.getEventContext().getEvent();
    }

    /**
     * Returns the original {@link EventContext} that was being processed when the
     * exception occurred.
     *
     * @return the wrapped event context
     */
    @NotNull EventContext<T> getEventContext();

    /**
     * Returns the {@link Throwable} that was caught during event processing.
     *
     * @return the caught exception
     */
    @NotNull Throwable getException();

    /** {@inheritDoc} */
    @Override
    default Mono<Guild> getGuild() {
        return this.getEventContext().getGuild();
    }

    /** {@inheritDoc} */
    @Override
    default Optional<Snowflake> getGuildId() {
        return this.getEventContext().getGuildId();
    }

    /** {@inheritDoc} */
    @Override
    default @NotNull User getInteractUser() {
        return this.getEventContext().getInteractUser();
    }

    /** {@inheritDoc} */
    @Override
    default @NotNull Snowflake getInteractUserId() {
        return this.getEventContext().getInteractUserId();
    }

    /** {@inheritDoc} */
    @Override
    default Mono<Void> reply(@NotNull Response response) {
        return this.getEventContext().reply(response);
    }

    /**
     * Returns a human-readable title describing the category of the exception, such as
     * {@code "Command Exception"} or {@code "Event Reply Exception"}.
     *
     * @return the exception title
     */
    @NotNull String getTitle();

    /**
     * Creates a new {@link ExceptionContext} wrapping a {@link CommandContext} with the
     * default title {@code "Command Exception"}.
     *
     * @param discordBot the bot instance
     * @param context the command context in which the exception occurred
     * @param throwable the caught exception
     * @return a new exception context
     */
    static @NotNull ExceptionContext<?> of(@NotNull DiscordBot discordBot, @NotNull CommandContext<?> context, @NotNull Throwable throwable) {
        return of(
            discordBot,
            context,
            throwable,
            "Command Exception"
        );
    }

    /**
     * Creates a new {@link ExceptionContext} wrapping the given {@link EventContext} with
     * the specified title.
     *
     * @param <T> the Discord4J event type
     * @param discordBot the bot instance
     * @param context the event context in which the exception occurred
     * @param throwable the caught exception
     * @param title a human-readable title for the exception category
     * @return a new exception context
     */
    static <T extends Event> @NotNull ExceptionContext<T> of(@NotNull DiscordBot discordBot, @NotNull EventContext<T> context, @NotNull Throwable throwable, @NotNull String title) {
        return new Impl<>(discordBot, context, throwable, title);
    }

    /**
     * Default implementation of {@link ExceptionContext} that stores the bot instance,
     * wrapped event context, exception, and title.
     *
     * @param <T> the Discord4J event type
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl<T extends Event> implements ExceptionContext<T> {

        /**
         * The bot instance that received this event.
         */
        private final @NotNull DiscordBot discordBot;

        /**
         * The original event context that was being processed when the exception occurred.
         */
        private final @NotNull EventContext<T> eventContext;

        /**
         * A randomly generated response identifier for this exception context.
         */
        private final @NotNull UUID responseId = UUID.randomUUID();

        /**
         * The caught exception.
         */
        private final @NotNull Throwable exception;

        /**
         * A human-readable title for the exception category.
         */
        private final @NotNull String title;

    }

}
