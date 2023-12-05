package dev.sbs.discordapi.context;

import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.context.message.MessageContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.util.cache.ResponseCache;
import discord4j.core.event.domain.Event;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public interface ResponseContext<T extends Event> extends MessageContext<T> {

    Mono<Message> discordBuildFollowup(@NotNull Response response);

    Mono<Void> discordDeleteFollowup(@NotNull String identifier);

    default Mono<Message> discordEditFollowup(@NotNull Response response) {
        return Mono.justOrEmpty(this.getFollowup()).flatMap(followup -> this.discordEditFollowup(followup.getIdentifier(), response));
    }

    Mono<Message> discordEditFollowup(@NotNull String identifier, @NotNull Response response);

    @Override
    default Mono<Message> discordEditMessage(@NotNull Response response) {
        return this.getMessage().flatMap(message -> message.edit(response.getD4jEditSpec()));
    }

    default Mono<Void> edit() {
        return Mono.justOrEmpty(this.getResponse()).flatMap(this::edit);
    }

    @Override
    default Mono<Void> edit(@NotNull Response response) {
        return this.discordEditMessage(response)
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
                .flatMap(entry -> entry.updateResponse(response)
                    .then(entry.updateReactions(message))
                    .then(entry.updateAttachments(message))
                    .then(entry.updateLastInteract())
                )
            )
            .then();
    }

    default Mono<Void> editFollowup() {
        return Mono.justOrEmpty(this.getResponse()).flatMap(this::editFollowup);
    }

    default Mono<Void> editFollowup(@NotNull Response response) {
        return Mono.justOrEmpty(this.getFollowup()).flatMap(followup -> this.editFollowup(followup.getIdentifier(), response));
    }

    default Mono<Void> editFollowup(@NotNull String identifier, @NotNull Response response) {
        return this.discordEditFollowup(identifier, response)
            .checkpoint("ResponseContext#editFollowup Processing")
            .onErrorResume(throwable -> this.getDiscordBot().handleException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    this,
                    throwable,
                    "Followup Edit Exception"
                )
            ))
            .then();
    }

    default Mono<Void> deleteFollowup() {
        return Mono.justOrEmpty(this.getFollowup()).flatMap(followup -> this.deleteFollowup(followup.getIdentifier()));
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
            .then(this.withResponseConsumer(entry -> entry.removeFollowup(identifier)));
    }

    default Mono<Void> followup(@NotNull Response response) {
        return this.followup(response.getUniqueId().toString(), response);
    }

    default Mono<Void> followup(@NotNull String identifier, @NotNull Response response) {
        return this.discordBuildFollowup(response)
            .onErrorResume(throwable -> this.getDiscordBot().handleException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    this,
                    throwable,
                    "Followup Create Exception"
                )
            ))
            .flatMap(message -> this.withResponseConsumer(entry -> entry.addFollowup(
                identifier,
                message.getChannelId(),
                this.getInteractUserId(),
                message.getId(),
                response
            )));
    }

    @NotNull Optional<ResponseCache.Followup> getFollowup();

    default @NotNull Optional<ResponseCache.Followup> getFollowup(@NotNull String identifier) {
        return this.getResponseCacheEntry().findFollowup(identifier);
    }

    default @NotNull Response getResponse() {
        return this.getResponseCacheEntry().getResponse();
    }

    default @NotNull ResponseCache.Entry getResponseCacheEntry() {
        return this.getDiscordBot()
            .getResponseCache()
            .findFirstOrNull(entry -> entry.getResponse().getUniqueId(), this.getResponseId());
    }

    default Mono<Void> withResponseConsumer(@NotNull Consumer<ResponseCache.Entry> consumer) {
        return Mono.just(this.getResponseCacheEntry())
            .doOnNext(consumer)
            .then();
    }

    default Mono<Void> withResponseFunction(@NotNull Function<ResponseCache.Entry, Mono<ResponseCache.Entry>> function) {
        return Mono.just(this.getResponseCacheEntry())
            .flatMap(function)
            .then();
    }

}
