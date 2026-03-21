package dev.sbs.discordapi.context.message;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.context.ExceptionContext;
import dev.sbs.discordapi.handler.exception.ExceptionHandler;
import dev.sbs.discordapi.handler.response.BaseEntry;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Specialization of {@link EventContext} for message-based events, adding support for
 * editing the originating message, creating and managing followup messages, and accessing
 * the cached {@link Response} and its {@link CachedResponse} entry.
 *
 * <p>
 * This interface resolves its channel from the underlying {@link Message} rather than
 * directly from the event, and provides a rich set of operations:
 * <ul>
 *   <li><b>Edit</b> - {@link #edit()}, {@link #edit(Function)}</li>
 *   <li><b>Followup</b> - {@link #followup(Response)}, {@link #editFollowup()},
 *       {@link #deleteFollowup()}</li>
 *   <li><b>Consume</b> - {@link #consumeResponse(Consumer)},
 *       {@link #withResponse(Function)}, {@link #withResponseEntry(Function)}</li>
 * </ul>
 *
 * @param <T> the Discord4J {@link Event} type wrapped by this context
 * @see EventContext
 * @see CachedResponse
 * @see Followup
 */
public interface MessageContext<T extends Event> extends EventContext<T> {

    /**
     * Passes the active {@link Response} to the given consumer. If a followup is present,
     * its response is used; otherwise the primary response is used.
     *
     * @param consumer the consumer to accept the response
     * @return a {@link Mono} completing after the consumer has been invoked
     */
    default Mono<Void> consumeResponse(@NotNull Consumer<Response> consumer) {
        return Mono.justOrEmpty(this.getFollowup())
            .map(Followup::getResponse)
            .switchIfEmpty(Mono.justOrEmpty(this.getResponse()))
            .flatMap(response -> {
                consumer.accept(response);
                return Mono.empty();
            });
    }

    /**
     * Builds a followup {@link Message} from the given {@link Response}, referencing the
     * original message as a reply.
     *
     * @param response the response to send as a followup
     * @return a {@link Mono} emitting the created followup message
     */
    default Mono<Message> discordBuildFollowup(@NotNull Response response) {
        return this.discordBuildMessage(
            response.mutate()
                .withReference(this.getMessageId())
                .build()
        );
    }

    /**
     * Deletes the Discord message associated with the followup identified by the given string.
     *
     * @param identifier the followup identifier
     * @return a {@link Mono} completing when the message has been deleted
     */
    default Mono<Void> discordDeleteFollowup(@NotNull String identifier) {
        return Mono.justOrEmpty(this.getFollowup(identifier))
            .flatMap(followup -> this.getChannel().flatMap(channel -> channel.getMessageById(followup.getMessageId())))
            .flatMap(Message::delete);
    }

    /**
     * Edits the default followup's Discord message with the given {@link Response}.
     *
     * @param response the response to apply as an edit
     * @return a {@link Mono} emitting the edited message
     */
    default Mono<Message> discordEditFollowup(@NotNull Response response) {
        return Mono.justOrEmpty(this.getFollowup())
            .flatMap(followup -> this.discordEditFollowup(followup.getIdentifier(), response))
            .publishOn(response.getReactorScheduler());
    }

    /**
     * Edits the Discord message for the followup with the given identifier, applying the
     * provided {@link Response}.
     *
     * @param identifier the followup identifier
     * @param response the response to apply as an edit
     * @return a {@link Mono} emitting the edited message
     */
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

    /**
     * Edits the original Discord message with the given {@link Response}.
     *
     * @param response the response to apply as an edit
     * @return a {@link Mono} emitting the edited message
     */
    default Mono<Message> discordEditMessage(@NotNull Response response) {
        return this.getMessage().flatMap(message -> message.edit(response.getD4jEditSpec()));
    }

    /**
     * Edits the Discord message identified by the given {@link Snowflake} with the provided
     * {@link Response}.
     *
     * @param messageId the id of the message to edit
     * @param response the response to apply as an edit
     * @return a {@link Mono} emitting the edited message
     */
    default Mono<Message> discordEditMessage(@NotNull Snowflake messageId, @NotNull Response response) {
        return this.getChannel()
            .flatMap(channel -> channel.getMessageById(messageId))
            .flatMap(message -> message.edit(response.getD4jEditSpec()));
    }

    /**
     * Deletes the default followup message and removes it from the cache.
     *
     * @return a {@link Mono} completing when the followup has been deleted
     */
    default Mono<Void> deleteFollowup() {
        return Mono.justOrEmpty(this.getFollowup())
            .flatMap(followup -> this.deleteFollowup(followup.getIdentifier()));
    }

    /**
     * Deletes the followup message identified by the given string and removes it from the
     * cache. Errors are forwarded to the {@link ExceptionHandler}.
     *
     * @param identifier the followup identifier
     * @return a {@link Mono} completing when the followup has been deleted
     */
    default Mono<Void> deleteFollowup(@NotNull String identifier) {
        return this.discordDeleteFollowup(identifier)
            .checkpoint("ResponseContext#deleteFollowup Processing")
            .onErrorResume(throwable -> this.getDiscordBot().getExceptionHandler().handleException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    this,
                    throwable,
                    "Followup Delete Exception"
                )
            ))
            .then(Mono.fromRunnable(() -> this.getResponseCacheEntry().removeFollowup(identifier)));
    }

    /**
     * Re-sends the current {@link Response} as an edit to the original message without
     * modifications.
     *
     * @return a {@link Mono} completing when the edit has been applied
     */
    default Mono<Void> edit() {
        return this.edit(__ -> __);
    }

    /**
     * Applies the given transformation to the current {@link Response}, edits the original
     * Discord message, and updates the cache entry. Errors are forwarded to the
     * {@link ExceptionHandler}.
     *
     * @param responseFunction the function that transforms the current response
     * @return a {@link Mono} completing when the edit has been applied and cached
     */
    default Mono<Void> edit(@NotNull Function<Response, Response> responseFunction) {
        Response editedResponse = responseFunction.apply(this.getResponse());

        return this.discordEditMessage(editedResponse)
            .checkpoint("ResponseContext#edit Processing")
            .onErrorResume(throwable -> this.getDiscordBot().getExceptionHandler().handleException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    this,
                    throwable,
                    "Response Edit Exception"
                )
            ))
            .flatMap(message -> Mono.just(this.getResponseCacheEntry())
                .flatMap(entry -> entry.updateResponse(editedResponse)
                    .then(entry.updateReactions(message))
                    .then(entry.updateAttachments(message))
                    .then(entry.updateLastInteract())
                )
            )
            .then();
    }

    /**
     * Re-sends the default followup's response as an edit without modifications.
     *
     * @return a {@link Mono} completing when the followup edit has been applied
     */
    default Mono<Void> editFollowup() {
        return this.editFollowup(BaseEntry::getResponse);
    }

    /**
     * Applies the given transformation to the default followup and edits its Discord message.
     *
     * @param responseFunction the function that extracts or transforms the response from the followup
     * @return a {@link Mono} completing when the followup edit has been applied
     */
    default Mono<Void> editFollowup(@NotNull Function<Followup, Response> responseFunction) {
        return Mono.justOrEmpty(this.getFollowup()).flatMap(followup -> this.editFollowup(followup.getIdentifier(), responseFunction));
    }

    /**
     * Applies the given transformation to the followup identified by the given string and
     * edits its Discord message. Errors are forwarded to the {@link ExceptionHandler}.
     *
     * @param identifier the followup identifier
     * @param responseFunction the function that extracts or transforms the response from the followup
     * @return a {@link Mono} completing when the followup edit has been applied and cached
     */
    default Mono<Void> editFollowup(@NotNull String identifier, @NotNull Function<Followup, Response> responseFunction) {
        return Mono.justOrEmpty(this.getFollowup(identifier))
            .flatMap(followup -> {
                Response editedResponse = responseFunction.apply(followup);

                return this.discordEditFollowup(identifier, editedResponse)
                    .checkpoint("ResponseContext#editFollowup Processing")
                    .onErrorResume(throwable -> this.getDiscordBot().getExceptionHandler().handleException(
                        ExceptionContext.of(
                            this.getDiscordBot(),
                            this,
                            throwable,
                            "Followup Edit Exception"
                        )
                    ))
                    .flatMap(message -> Mono.just(this.getResponseCacheEntry())
                        .flatMap(entry -> followup.updateResponse(editedResponse)
                            .then(followup.updateReactions(message))
                            .then(followup.updateAttachments(message))
                            .then(entry.updateLastInteract())
                        )
                    );
            })
            .then();
    }

    /**
     * Sends the given {@link Response} as a followup message referencing the original
     * message, using the response's unique id as the followup identifier.
     *
     * @param response the response to send as a followup
     * @return a {@link Mono} completing when the followup has been sent and cached
     */
    default Mono<Void> followup(@NotNull Response response) {
        return this.followup(response.getUniqueId().toString(), response);
    }

    /**
     * Sends the given {@link Response} as a followup message with the specified identifier,
     * referencing the original message. The followup is registered in the
     * {@link CachedResponse} and its reactions, attachments, and last-interact timestamp
     * are updated. Errors are forwarded to the {@link ExceptionHandler}.
     *
     * @param identifier the identifier for this followup
     * @param response the response to send as a followup
     * @return a {@link Mono} completing when the followup has been sent and cached
     */
    default Mono<Void> followup(@NotNull String identifier, @NotNull Response response) {
        return this.discordBuildFollowup(response)
            .checkpoint("ResponseContext#followup Processing")
            .onErrorResume(throwable -> this.getDiscordBot().getExceptionHandler().handleException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    this,
                    throwable,
                    "Followup Create Exception"
                )
            ))
            .flatMap(message -> Mono.just(this.getResponseCacheEntry()).flatMap(
                entry -> entry.addFollowup(
                        identifier,
                        message.getChannelId(),
                        this.getInteractUserId(),
                        message.getId(),
                        response
                    )
                    .flatMap(followup -> followup.updateReactions(message)
                        .then(followup.updateAttachments(message))
                        .then(entry.updateLastInteract())
                    )
            ))
            .then();
    }

    /** {@inheritDoc} */
    @Override
    default Mono<MessageChannel> getChannel() {
        return this.getMessage().flatMap(Message::getChannel);
    }

    /**
     * Returns the default {@link Followup} associated with this context, if one exists.
     *
     * @return an {@link Optional} containing the default followup, or empty if none is present
     */
    @NotNull Optional<Followup> getFollowup();

    /**
     * Returns the {@link Followup} with the given identifier from the cached response entry.
     *
     * @param identifier the followup identifier to look up
     * @return an {@link Optional} containing the matching followup, or empty if not found
     */
    default @NotNull Optional<Followup> getFollowup(@NotNull String identifier) {
        return this.getResponseCacheEntry().findFollowup(identifier);
    }

    /**
     * Returns the Discord {@link Message} associated with this context.
     *
     * @return a {@link Mono} emitting the message
     */
    Mono<Message> getMessage();

    /**
     * Returns the {@link Snowflake} identifier of the message associated with this context.
     *
     * @return the message snowflake id
     */
    Snowflake getMessageId();

    /**
     * Returns the cached {@link Response} from the response handler.
     *
     * @return the active response
     */
    default @NotNull Response getResponse() {
        return this.getResponseCacheEntry().getResponse();
    }

    /**
     * Returns the {@link CachedResponse} entry for this context's response id.
     *
     * @return the cached response entry
     */
    default @NotNull CachedResponse getResponseCacheEntry() {
        return this.getDiscordBot()
            .getResponseHandler()
            .findFirstOrNull(entry -> entry.getResponse().getUniqueId(), this.getResponseId());
    }

    /**
     * Executes the given function with the active {@link Response}. If a followup is
     * present, its response is used; otherwise the primary response is used.
     *
     * @param function the function to apply to the response
     * @return a {@link Mono} completing when the function finishes
     */
    default Mono<Void> withResponse(@NotNull Function<Response, Mono<Void>> function) {
        return Mono.justOrEmpty(this.getFollowup())
            .map(Followup::getResponse)
            .switchIfEmpty(Mono.justOrEmpty(this.getResponse()))
            .flatMap(function);
    }

    /**
     * Executes the given function with the {@link CachedResponse} entry for this context.
     *
     * @param function the function to apply to the cached response entry
     * @return a {@link Mono} completing when the function finishes
     */
    default Mono<Void> withResponseEntry(@NotNull Function<CachedResponse, Mono<CachedResponse>> function) {
        return Mono.just(this.getResponseCacheEntry())
            .flatMap(function)
            .then();
    }

    /**
     * Creates a new {@link Create} context from a {@link MessageCreateEvent}.
     *
     * @param discordBot the bot instance
     * @param event the message create event
     * @param cachedMessage the cached response whose unique id identifies this context
     * @param followup an optional followup associated with this message
     * @return a new {@code Create} context
     */
    static @NotNull Create ofCreate(@NotNull DiscordBot discordBot, @NotNull MessageCreateEvent event, @NotNull Response cachedMessage, @NotNull Optional<Followup> followup) {
        return new Create(
            discordBot,
            event,
            cachedMessage.getUniqueId(),
            new User(discordBot.getGateway(), event.getMessage().getUserData()),
            followup
        );
    }

    /**
     * Concrete {@link MessageContext} implementation for {@link MessageCreateEvent} instances,
     * providing access to the newly created message's channel, guild, and author.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    class Create implements MessageContext<MessageCreateEvent> {

        /**
         * The bot instance that received this event.
         */
        private final @NotNull DiscordBot discordBot;

        /**
         * The underlying message create event.
         */
        private final @NotNull MessageCreateEvent event;

        /**
         * The unique identifier of the cached response.
         */
        private final @NotNull UUID responseId;

        /**
         * The user who created the message.
         */
        private final @NotNull User interactUser;

        /**
         * The default followup associated with this context, if any.
         */
        private final @NotNull Optional<Followup> followup;

        /** {@inheritDoc} */
        @Override
        public @NotNull Snowflake getChannelId() {
            return this.getEvent().getMessage().getChannelId();
        }

        /** {@inheritDoc} */
        @Override
        public Mono<Guild> getGuild() {
            return this.getEvent().getGuild();
        }

        /** {@inheritDoc} */
        @Override
        public Optional<Snowflake> getGuildId() {
            return this.getEvent().getGuildId();
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull Snowflake getInteractUserId() {
            return this.getInteractUser().getId();
        }

        /** {@inheritDoc} */
        @Override
        public Mono<Message> getMessage() {
            return Mono.just(this.getEvent().getMessage());
        }

        /** {@inheritDoc} */
        @Override
        public Snowflake getMessageId() {
            return this.getEvent().getMessage().getId();
        }

    }

}
