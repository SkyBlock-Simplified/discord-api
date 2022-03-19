package dev.sbs.discordapi.context;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.data.model.discord.emojis.EmojiModel;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.util.cache.DiscordResponseCache;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.Event;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.PrivateChannel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public interface EventContext<T extends Event> {

    default Mono<Message> buildMessage(Response response) {
        return this.getChannel().flatMap(response::getD4jCreateMono);
    }

    default Mono<Void> deferReply() {
        return this.deferReply(false);
    }

    default Mono<Void> deferReply(boolean ephemeral) {
        return Mono.defer(() -> this.reply(
            Response.builder(this)
                .isInteractable()
                .isLoader()
                .isEphemeral(ephemeral)
                .withReference(this)
                .withPages(
                    Page.builder()
                        .withContent(
                            FormatUtil.format(
                                "{0}{1} is working...",
                                SimplifiedApi.getRepositoryOf(EmojiModel.class)
                                    .findFirst(EmojiModel::getKey, "LOADING_RIPPLE")
                                    .flatMap(Emoji::of)
                                    .map(Emoji::asSpacedFormat)
                                    .orElse(""),
                                this.getDiscordBot().getSelf().getUsername()
                            )
                        )
                        .build()
                )
                .build()
        ));
    }

    Mono<MessageChannel> getChannel();

    Snowflake getChannelId();

    DiscordBot getDiscordBot();

    T getEvent();

    Mono<Guild> getGuild();

    Optional<Snowflake> getGuildId();

    default Optional<String> getIdentifier() {
        return Optional.empty();
    }

    User getInteractUser();

    Snowflake getInteractUserId();

    default Mono<PrivateChannel> getInteractUserPrivateChannel() {
        return this.getInteractUser().getPrivateChannel();
    }

    UUID getUniqueId();

    default boolean isPrivateChannel() {
        return this.getGuildId().isEmpty();
    }

    default Mono<Void> reply(Response response) {
        return Flux.fromIterable(this.getDiscordBot().getResponseCache())
            .filter(entry -> entry.getResponse().getUniqueId().equals(this.getUniqueId()))
            .filter(entry -> entry.getResponse().isLoader())
            .singleOrEmpty()
            .flatMap(deferredReply -> {
                deferredReply.updateResponse(response);
                deferredReply.setUpdated();

                return this.getChannel()
                    .flatMap(channel -> channel.getMessageById(deferredReply.getMessageId()))
                    .flatMap(message -> message.edit(response.getD4jEditSpec()))
                    .flatMap(message -> Flux.fromIterable(response.getCurrentPage().getReactions())
                        .flatMap(emoji -> message.addReaction(emoji.getD4jReaction()))
                        .then(Mono.just(deferredReply))
                    );
            })
            .switchIfEmpty(
                this.buildMessage(response)
                    .checkpoint(FormatUtil.format("Response Processing{0}", this.getIdentifier().map(identifier -> ": " + identifier).orElse("")))
                    .onErrorResume(throwable -> this.getDiscordBot().handleException(
                        ExceptionContext.of(
                            this.getDiscordBot(),
                            this,
                            throwable,
                            "Response Exception"
                        )
                    ))
                    .flatMap(message -> Flux.fromIterable(response.getCurrentPage().getReactions())
                        .flatMap(emoji -> message.addReaction(emoji.getD4jReaction()))
                        .then(Mono.fromRunnable(() -> {
                            if (response.isInteractable()) {
                                // Cache Message
                                DiscordResponseCache.Entry responseCacheEntry = this.getDiscordBot()
                                    .getResponseCache()
                                    .add(
                                        message.getChannelId(),
                                        this.getInteractUserId(),
                                        message.getId(),
                                        response
                                    );

                                if (!response.isLoader()) {
                                    responseCacheEntry.updateLastInteract(); // Update TTL
                                    responseCacheEntry.setUpdated();
                                }
                            }
                        }))
                    )
            )
            .then();
    }

    default Mono<Void> withChannel(Function<MessageChannel, Mono<Void>> messageChannelFunction) {
        return this.getChannel().flatMap(messageChannelFunction).then();
    }

    default Mono<Void> withEvent(Function<T, Mono<Void>> eventFunction) {
        return Mono.just(this.getEvent()).flatMap(eventFunction).then();
    }

}
