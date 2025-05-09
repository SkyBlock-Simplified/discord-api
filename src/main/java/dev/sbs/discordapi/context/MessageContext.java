package dev.sbs.discordapi.context;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.exception.ExceptionContext;
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

public interface MessageContext<T extends Event> extends EventContext<T> {

    default Mono<Void> consumeResponse(@NotNull Consumer<Response> consumer) {
        return Mono.justOrEmpty(this.getFollowup())
            .map(Followup::getResponse)
            .switchIfEmpty(Mono.justOrEmpty(this.getResponse()))
            .flatMap(response -> {
                consumer.accept(response);
                return Mono.empty();
            });
    }

    default Mono<Message> discordBuildFollowup(@NotNull Response response) {
        return this.discordBuildMessage(
            response.mutate()
                .withReference(this.getMessageId())
                .build()
        );
    }

    default Mono<Void> discordDeleteFollowup(@NotNull String identifier) {
        return Mono.justOrEmpty(this.getFollowup(identifier))
            .flatMap(followup -> this.getChannel().flatMap(channel -> channel.getMessageById(followup.getMessageId())))
            .flatMap(Message::delete);
    }

    default Mono<Message> discordEditFollowup(@NotNull Response response) {
        return Mono.justOrEmpty(this.getFollowup())
            .flatMap(followup -> this.discordEditFollowup(followup.getIdentifier(), response))
            .publishOn(response.getReactorScheduler());
    }

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

    default Mono<Message> discordEditMessage(@NotNull Response response) {
        return this.getMessage().flatMap(message -> message.edit(response.getD4jEditSpec()));
    }

    default Mono<Message> discordEditMessage(@NotNull Snowflake messageId, @NotNull Response response) {
        return this.getChannel()
            .flatMap(channel -> channel.getMessageById(messageId))
            .flatMap(message -> message.edit(response.getD4jEditSpec()));
    }

    default Mono<Void> deleteFollowup() {
        return Mono.justOrEmpty(this.getFollowup())
            .flatMap(followup -> this.deleteFollowup(followup.getIdentifier()));
    }

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

    default Mono<Void> edit() {
        return this.edit(__ -> __);
    }

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

    default Mono<Void> editFollowup() {
        return this.editFollowup(BaseEntry::getResponse);
    }

    default Mono<Void> editFollowup(@NotNull Function<Followup, Response> responseFunction) {
        return Mono.justOrEmpty(this.getFollowup()).flatMap(followup -> this.editFollowup(followup.getIdentifier(), responseFunction));
    }

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

    default Mono<Void> followup(@NotNull Response response) {
        return this.followup(response.getUniqueId().toString(), response);
    }

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

    @Override
    default Mono<MessageChannel> getChannel() {
        return this.getMessage().flatMap(Message::getChannel);
    }

    @NotNull Optional<Followup> getFollowup();

    default @NotNull Optional<Followup> getFollowup(@NotNull String identifier) {
        return this.getResponseCacheEntry().findFollowup(identifier);
    }

    Mono<Message> getMessage();

    Snowflake getMessageId();

    default @NotNull Response getResponse() {
        return this.getResponseCacheEntry().getResponse();
    }

    default @NotNull CachedResponse getResponseCacheEntry() {
        return this.getDiscordBot()
            .getResponseHandler()
            .findFirstOrNull(entry -> entry.getResponse().getUniqueId(), this.getResponseId());
    }

    default Mono<Void> withResponse(@NotNull Function<Response, Mono<Void>> function) {
        return Mono.justOrEmpty(this.getFollowup())
            .map(Followup::getResponse)
            .switchIfEmpty(Mono.justOrEmpty(this.getResponse()))
            .flatMap(function);
    }

    default Mono<Void> withResponseEntry(@NotNull Function<CachedResponse, Mono<CachedResponse>> function) {
        return Mono.just(this.getResponseCacheEntry())
            .flatMap(function)
            .then();
    }

    static @NotNull Create ofCreate(@NotNull DiscordBot discordBot, @NotNull MessageCreateEvent event, @NotNull Response cachedMessage, @NotNull Optional<Followup> followup) {
        return new Create(
            discordBot,
            event,
            cachedMessage.getUniqueId(),
            new User(discordBot.getGateway(), event.getMessage().getUserData()),
            followup
        );
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    class Create implements MessageContext<MessageCreateEvent> {

        private final @NotNull DiscordBot discordBot;
        private final @NotNull MessageCreateEvent event;
        private final @NotNull UUID responseId;
        private final @NotNull User interactUser;
        private final @NotNull Optional<Followup> followup;

        @Override
        public @NotNull Snowflake getChannelId() {
            return this.getEvent().getMessage().getChannelId();
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
        public @NotNull Snowflake getInteractUserId() {
            return this.getInteractUser().getId();
        }

        @Override
        public Mono<Message> getMessage() {
            return Mono.just(this.getEvent().getMessage());
        }

        @Override
        public Snowflake getMessageId() {
            return this.getEvent().getMessage().getId();
        }

    }

}
