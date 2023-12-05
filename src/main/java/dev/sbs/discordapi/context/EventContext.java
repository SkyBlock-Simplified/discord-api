package dev.sbs.discordapi.context;

import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.util.cache.ResponseCache;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.Event;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.PrivateChannel;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public interface EventContext<T extends Event> {

    default Mono<Void> deferReply() {
        return this.deferReply(false);
    }

    default Mono<Void> deferReply(boolean ephemeral) {
        return this.deferReply(ephemeral, Optional.empty());
    }

    default Mono<Void> deferReply(boolean ephemeral, @PrintFormat @Nullable String content, @Nullable Object... args) {
        return this.deferReply(ephemeral, StringUtil.formatNullable(content, args));
    }

    default Mono<Void> deferReply(boolean ephemeral, @NotNull Optional<String> content) {
        return Mono.defer(() -> this.reply(Response.loader(this, ephemeral, content)));
    }

    default Mono<Message> discordBuildMessage(@NotNull Response response) {
        return this.getChannel()
            .flatMap(response::getD4jCreateMono)
            .publishOn(response.getReactorScheduler());
    }

    default Mono<Message> discordEditMessage(@NotNull Response response) {
        return Flux.fromIterable(this.getDiscordBot().getResponseCache())
            .filter(entry -> entry.getResponse().getUniqueId().equals(response.getUniqueId()))
            .singleOrEmpty()
            .flatMap(entry -> this.discordEditMessage(entry.getMessageId(), response));
    }

    default Mono<Message> discordEditMessage(@NotNull Snowflake messageId, @NotNull Response response) {
        return this.getChannel()
            .flatMap(channel -> channel.getMessageById(messageId))
            .flatMap(message -> message.edit(response.getD4jEditSpec()));
    }

    default Mono<Void> edit(@PrintFormat @Nullable String content, @Nullable Object... args) {
        return this.edit(StringUtil.formatNullable(content, args));
    }

    default Mono<Void> edit(@NotNull Optional<String> content) {
        return this.edit(Response.loader(this, false, content));
    }

    default Mono<Void> edit(@NotNull Response response) {
        return Flux.fromIterable(this.getDiscordBot().getResponseCache())
            .checkpoint("EventContext#edit Processing")
            .filter(entry -> entry.getResponse().getUniqueId().equals(response.getUniqueId()))
            .singleOrEmpty()
            .onErrorResume(throwable -> this.getDiscordBot().handleException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    this,
                    throwable,
                    "Response Edit Exception"
                )
            ))
            .flatMap(entry -> this.discordEditMessage(response)
                .flatMap(message -> entry.updateResponse(response)
                    .updateAttachments(message)
                    .updateReactions(message)
                    .then(entry.updateLastInteract())
                )
            )
            .then();
    }

    Mono<MessageChannel> getChannel();

    @NotNull Snowflake getChannelId();

    @NotNull DiscordBot getDiscordBot();

    @NotNull T getEvent();

    Mono<Guild> getGuild();

    Optional<Snowflake> getGuildId();

    @NotNull User getInteractUser();

    @NotNull Snowflake getInteractUserId();

    default Mono<PrivateChannel> getInteractUserPrivateChannel() {
        return this.getInteractUser().getPrivateChannel();
    }

    @NotNull UUID getResponseId();

    default boolean isPrivateChannel() {
        return this.getGuildId().isEmpty();
    }

    default boolean isGuildChannel() {
        return this.getGuildId().isPresent();
    }

    default Mono<Void> reply(@NotNull Response response) {
        return Flux.fromIterable(this.getDiscordBot().getResponseCache())
            .checkpoint("EventContext#reply Processing")
            .onErrorResume(throwable -> this.getDiscordBot().handleException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    this,
                    throwable,
                    "Response Reply Exception"
                )
            ))
            .filter(entry -> entry.getResponse().getUniqueId().equals(this.getResponseId())) // Search For Loader
            .filter(ResponseCache.Entry::isLoading)
            .singleOrEmpty()
            .flatMap(entry -> entry.setLoaded() // Final Edit Message
                .updateResponse(response)
                .updateLastInteract()
                .then(this.discordEditMessage(response))
                .doOnNext(entry::updateAttachments)
                .flatMap(entry::updateReactions)
            )
            .switchIfEmpty(
                this.discordBuildMessage(response) // Create New Message
                    .flatMap(message -> {
                        if (response.isInteractable()) {
                            // Cache Message
                            return this.getDiscordBot()
                                .getResponseCache()
                                .createAndGet(
                                    message.getChannelId(),
                                    this.getInteractUserId(),
                                    message.getId(),
                                    response
                                )
                                .updateLastInteract()
                                .doOnNext(entry -> entry.updateAttachments(message))
                                .flatMap(entry -> entry.updateReactions(message))
                                .thenReturn(message);
                        }

                        return Mono.just(message);
                    })
            )
            .then();
    }

    default Mono<Void> withChannel(Function<MessageChannel, Mono<Void>> messageChannelFunction) {
        return this.getChannel().flatMap(messageChannelFunction);
    }

    default Mono<Void> withEvent(Function<T, Mono<Void>> eventFunction) {
        return Mono.just(this.getEvent()).flatMap(eventFunction);
    }

    default Mono<Void> withGuild(Function<Optional<Guild>, Mono<Void>> guildFunction) {
        return this.getGuildId().isPresent() ? this.getGuild().flatMap(guild -> guildFunction.apply(Optional.of(guild))) : guildFunction.apply(Optional.empty());
    }

}
