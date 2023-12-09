package dev.sbs.discordapi.context;

import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.util.cache.ResponseCache;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.Event;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Function;

public interface MessageContext<T extends Event> extends EventContext<T> {

    Mono<Message> discordBuildFollowup(@NotNull Response response);

    Mono<Void> discordDeleteFollowup(@NotNull String identifier);

    default Mono<Message> discordEditFollowup(@NotNull Response response) {
        return Mono.justOrEmpty(this.getFollowup())
            .flatMap(followup -> this.discordEditFollowup(followup.getIdentifier(), response))
            .publishOn(response.getReactorScheduler());
    }

    Mono<Message> discordEditFollowup(@NotNull String identifier, @NotNull Response response);

    default Mono<Message> discordEditMessage(@NotNull Response response) {
        return this.getMessage().flatMap(message -> message.edit(response.getD4jEditSpec()));
    }

    default Mono<Message> discordEditMessage(@NotNull Snowflake messageId, @NotNull Response response) {
        return this.getChannel()
            .flatMap(channel -> channel.getMessageById(messageId))
            .flatMap(message -> message.edit(response.getD4jEditSpec()));
    }

    default Mono<Void> edit() {
        return this.edit(__ -> __);
    }

    default Mono<Void> edit(@NotNull Function<Response, Response> responseFunction) {
        Response editedResponse = responseFunction.apply(this.getResponse());

        return this.discordEditMessage(editedResponse)
            .checkpoint("ResponseContext#edit Processing")
            .onErrorResume(throwable -> this.getDiscordBot().handleException(
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
        return this.editFollowup(ResponseCache.BaseEntry::getResponse);
    }

    default Mono<Void> editFollowup(@NotNull Function<ResponseCache.Followup, Response> responseFunction) {
        return Mono.justOrEmpty(this.getFollowup()).flatMap(followup -> this.editFollowup(followup.getIdentifier(), responseFunction));
    }

    default Mono<Void> editFollowup(@NotNull String identifier, @NotNull Function<ResponseCache.Followup, Response> responseFunction) {
        return Mono.justOrEmpty(this.getFollowup(identifier))
            .flatMap(followup -> {
                Response editedResponse = responseFunction.apply(followup);

                return this.discordEditFollowup(identifier, editedResponse)
                    .checkpoint("ResponseContext#editFollowup Processing")
                    .onErrorResume(throwable -> this.getDiscordBot().handleException(
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

    default Mono<Void> deleteFollowup() {
        return Mono.justOrEmpty(this.getFollowup())
            .flatMap(followup -> this.deleteFollowup(followup.getIdentifier()));
    }

    default Mono<Void> deleteFollowup(@NotNull String identifier) {
        return this.discordDeleteFollowup(identifier)
            .checkpoint("ResponseContext#deleteFollowup Processing")
            .onErrorResume(throwable -> this.getDiscordBot().handleException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    this,
                    throwable,
                    "Followup Delete Exception"
                )
            ))
            .then(Mono.fromRunnable(() -> this.getResponseCacheEntry().removeFollowup(identifier)));
    }

    default Mono<Void> followup(@NotNull Response response) {
        return this.followup(response.getUniqueId().toString(), response);
    }

    default Mono<Void> followup(@NotNull String identifier, @NotNull Response response) {
        return this.discordBuildFollowup(response)
            .checkpoint("ResponseContext#followup Processing")
            .onErrorResume(throwable -> this.getDiscordBot().handleException(
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

    @NotNull Optional<ResponseCache.Followup> getFollowup();

    default @NotNull Optional<ResponseCache.Followup> getFollowup(@NotNull String identifier) {
        return this.getResponseCacheEntry().findFollowup(identifier);
    }

    Mono<Message> getMessage();

    Snowflake getMessageId();

    default @NotNull Response getResponse() {
        return this.getResponseCacheEntry().getResponse();
    }

    default @NotNull ResponseCache.Entry getResponseCacheEntry() {
        return this.getDiscordBot()
            .getResponseCache()
            .findFirstOrNull(entry -> entry.getResponse().getUniqueId(), this.getResponseId());
    }

    default Mono<Void> withResponseFunction(@NotNull Function<ResponseCache.Entry, Mono<ResponseCache.Entry>> function) {
        return Mono.just(this.getResponseCacheEntry())
            .flatMap(function)
            .then();
    }

}
