package dev.sbs.discordapi.context;

import dev.sbs.api.util.helper.FormatUtil;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public interface EventContext<T extends Event> {

    default Mono<Message> buildMessage(@NotNull Response response) {
        return this.getChannel()
            .flatMap(response::getD4jCreateMono)
            .publishOn(response.getReactorScheduler());
    }

    default Mono<Void> deferReply() {
        return this.deferReply(false);
    }

    default Mono<Void> deferReply(boolean ephemeral) {
        return this.deferReply(ephemeral, Optional.empty());
    }

    default Mono<Void> deferReply(boolean ephemeral, @Nullable String content, @NotNull Object... objects) {
        return this.deferReply(ephemeral, FormatUtil.formatNullable(content, objects));
    }

    default Mono<Void> deferReply(boolean ephemeral, @NotNull Optional<String> content) {
        return Mono.defer(() -> this.reply(Response.loader(this, ephemeral, content)));
    }

    default Mono<Void> edit(@Nullable String content, @NotNull Object... objects) {
        return this.edit(FormatUtil.formatNullable(content, objects));
    }

    default Mono<Void> edit(@NotNull Optional<String> content) {
        return this.edit(Response.loader(this, false, content));
    }

    default Mono<Void> edit(@NotNull Response response) {
        return Flux.fromIterable(this.getDiscordBot().getResponseCache())
            .checkpoint("EventContext#edit Processing")
            .filter(entry -> entry.getResponse().getUniqueId().equals(this.getResponseId()))
            .filter(ResponseCache.Entry::isLoading) // Only Process Loading Responses Here
            .singleOrEmpty()
            .onErrorResume(throwable -> this.getDiscordBot().handleException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    this,
                    throwable,
                    "Response Edit Exception"
                )
            ))
            .flatMap(entry -> this.getChannel()
                .flatMap(channel -> channel.getMessageById(entry.getMessageId()))
                .flatMap(message -> message.edit(response.getD4jEditSpec()))
                .flatMap(message -> this.getDiscordBot().handleReactions(response, message))
                .then(Mono.fromRunnable(() -> {
                    entry.updateResponse(response);
                    entry.setUpdated();
                }))
            );
    }

    Mono<MessageChannel> getChannel();

    @NotNull Snowflake getChannelId();

    @NotNull DiscordBot getDiscordBot();

    T getEvent();

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

    default Mono<Void> reply(@NotNull Response response) {
        return Flux.fromIterable(this.getDiscordBot().getResponseCache())
            .checkpoint("EventContext#reply Processing")
            .filter(entry -> entry.getResponse().getUniqueId().equals(this.getResponseId()))
            .filter(ResponseCache.Entry::isLoading)
            .singleOrEmpty()
            .doOnNext(ResponseCache.Entry::setLoaded)
            .flatMap(entry -> this.edit(response)) // Final Edit Message
            .switchIfEmpty(
                this.buildMessage(response) // Create New Message
                    .flatMap(message -> this.getDiscordBot().handleReactions(response, message))
                    .onErrorResume(throwable -> this.getDiscordBot().handleException(
                        ExceptionContext.of(
                            this.getDiscordBot(),
                            this,
                            throwable,
                            "Response Create Exception"
                        )
                    ))
                    .flatMap(message -> Mono.fromRunnable(() -> {
                        if (response.isInteractable()) {
                            // Cache Message
                            ResponseCache.Entry responseCacheEntry = this.getDiscordBot()
                                .getResponseCache()
                                .createAndGet(
                                    message.getChannelId(),
                                    this.getInteractUserId(),
                                    message.getId(),
                                    response
                                );

                            responseCacheEntry.updateLastInteract();
                            responseCacheEntry.setUpdated();
                        }
                    }))
            );
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
