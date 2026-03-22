package dev.sbs.discordapi.context;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.command.TypingContext;
import dev.sbs.discordapi.context.message.MessageContext;
import dev.sbs.discordapi.context.message.ReactionContext;
import dev.sbs.discordapi.handler.exception.ExceptionHandler;
import dev.sbs.discordapi.handler.response.ResponseHandler;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.Event;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.PrivateChannel;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Root context interface wrapping a Discord4J {@link Event}, providing access to the
 * {@link DiscordBot} instance, channel and guild metadata, and common reply operations.
 *
 * <p>
 * All specialized context types in the hierarchy extend this interface, inheriting the
 * ability to resolve the originating channel, guild, and interacting user, as well as
 * the {@link #reply(Response)} convenience method that builds, sends, and caches a
 * {@link Response}.
 *
 * <p>
 * The full context hierarchy:
 * <ul>
 *   <li><b>{@code EventContext}</b> - this interface (root)</li>
 *   <li><b>{@link MessageContext}</b> - message-based events with followup support</li>
 *   <li><b>{@link TypingContext TypingContext}</b> - events carrying command structure metadata</li>
 *   <li><b>{@link ReactionContext ReactionContext}</b> - reaction add/remove events</li>
 *   <li><b>{@link ExceptionContext}</b> - exception-wrapping decorator</li>
 *   <li><b>{@link InteractionContext}</b> - Discord interaction events</li>
 * </ul>
 *
 * @param <T> the Discord4J {@link Event} type wrapped by this context
 */
public interface EventContext<T extends Event> {

    /**
     * Builds a Discord message from the given {@link Response} by sending it to the
     * resolved {@link MessageChannel}.
     *
     * @param response the response to send
     * @return a {@link Mono} emitting the created {@link Message}
     */
    default Mono<Message> discordBuildMessage(@NotNull Response response) {
        return this.getChannel()
            .flatMap(response::getD4jCreateMono)
            .publishOn(response.getReactorScheduler());
    }

    /** The {@link MessageChannel} in which this event occurred. */
    Mono<MessageChannel> getChannel();

    /** The {@link Snowflake} identifier of the channel in which this event occurred. */
    @NotNull Snowflake getChannelId();

    /** The {@link DiscordBot} instance that received this event. */
    @NotNull DiscordBot getDiscordBot();

    /** The underlying Discord4J {@link Event} wrapped by this context. */
    @NotNull T getEvent();

    /** The {@link Guild} associated with this event, if the event occurred in a guild channel. */
    Mono<Guild> getGuild();

    /** The {@link Snowflake} identifier of the guild, if the event occurred in a guild channel. */
    Optional<Snowflake> getGuildId();

    /** The {@link User} who triggered this event. */
    @NotNull User getInteractUser();

    /** The {@link Snowflake} identifier of the user who triggered this event. */
    @NotNull Snowflake getInteractUserId();

    /** The {@link PrivateChannel} (DM channel) for the interacting user. */
    default Mono<PrivateChannel> getInteractUserPrivateChannel() {
        return this.getInteractUser().getPrivateChannel();
    }

    /** The unique {@link UUID} identifying the {@link Response} associated with this context. */
    @NotNull UUID getResponseId();

    /**
     * Checks whether this event occurred in a private (DM) channel.
     *
     * @return {@code true} if no guild id is present
     */
    default boolean isPrivateChannel() {
        return this.getGuildId().isEmpty();
    }

    /**
     * Checks whether this event occurred in a guild channel.
     *
     * @return {@code true} if a guild id is present
     */
    default boolean isGuildChannel() {
        return this.getGuildId().isPresent();
    }

    /**
     * Sends the given {@link Response} as a new message, registers it with the
     * {@link ResponseHandler}, and updates its reactions, attachments, and last-interact
     * timestamp.
     *
     * <p>
     * Errors during message creation are forwarded to the {@link ExceptionHandler}.
     *
     * @param response the response to send
     * @return a {@link Mono} completing when the reply has been sent and cached
     */
    default Mono<Void> reply(@NotNull Response response) {
        return this.discordBuildMessage(response)
            .checkpoint("EventContext#reply Processing")
            .onErrorResume(throwable -> this.getDiscordBot().getExceptionHandler().handleException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    this,
                    throwable,
                    "Event Reply Exception"
                )
            ))
            .flatMap(message -> this.getDiscordBot()
                .getResponseHandler()
                .createAndGet(
                    message.getChannelId(),
                    this.getInteractUserId(),
                    message.getId(),
                    response
                )
                .updateResponse(response)
                .flatMap(entry -> entry.updateReactions(message)
                    .then(entry.updateAttachments(message))
                    .then(entry.updateLastInteract())
                )
                .then()
            );
    }

    /**
     * Executes the given function with the resolved {@link MessageChannel}.
     *
     * @param messageChannelFunction the function to apply to the channel
     * @return a {@link Mono} completing when the function finishes
     */
    default Mono<Void> withChannel(Function<MessageChannel, Mono<Void>> messageChannelFunction) {
        return this.getChannel().flatMap(messageChannelFunction);
    }

    /**
     * Executes the given function with the underlying {@link Event}.
     *
     * @param eventFunction the function to apply to the event
     * @return a {@link Mono} completing when the function finishes
     */
    default Mono<Void> withEvent(Function<T, Mono<Void>> eventFunction) {
        return Mono.just(this.getEvent()).flatMap(eventFunction);
    }

    /**
     * Executes the given function with the {@link Guild} wrapped in an {@link Optional}.
     *
     * <p>
     * If this event occurred in a guild channel, the optional will contain the guild;
     * otherwise it will be empty.
     *
     * @param guildFunction the function to apply to the optional guild
     * @return a {@link Mono} completing when the function finishes
     */
    default Mono<Void> withGuild(Function<Optional<Guild>, Mono<Void>> guildFunction) {
        return this.getGuildId().isPresent() ? this.getGuild().flatMap(guild -> guildFunction.apply(Optional.of(guild))) : guildFunction.apply(Optional.empty());
    }

}
