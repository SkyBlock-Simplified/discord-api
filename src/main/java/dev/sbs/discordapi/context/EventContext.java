package dev.sbs.discordapi.context;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.data.model.discord.emojis.EmojiModel;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.util.DiscordResponseCache;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.PrivateChannel;
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
        return Mono.defer(() -> this.reply(
            Response.builder(this)
                .isInteractable(false)
                .isLoader()
                .isEphemeral(ephemeral)
                .withReference(this)
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
        ));
    }

    Mono<MessageChannel> getChannel();

    Snowflake getChannelId();

    default GatewayDiscordClient getClient() {
        return this.getEvent().getClient();
    }

    DiscordBot getDiscordBot();

    T getEvent();

    Mono<Guild> getGuild();

    Optional<Snowflake> getGuildId();

    default Optional<String> getIdentifier() {
        return Optional.empty();
    }

    Mono<User> getInteractUser();

    Snowflake getInteractUserId();

    default Mono<PrivateChannel> getInteractUserPrivateChannel() {
        return this.getInteractUser().flatMap(User::getPrivateChannel);
    }

    UUID getUniqueId();

    default boolean isPrivateChannel() {
        return this.getGuildId().isEmpty();
    }

    default Mono<Void> reply(Response response) {
        return Flux.fromIterable(this.getDiscordBot().getResponseCache())
            .filter(entry -> entry.getResponse().getUniqueId().equals(this.getUniqueId()))
            .filter(entry -> entry.getResponse().isLoader())
            .flatMap(deferredReply -> {
                deferredReply.updateResponse(response);
                deferredReply.setUpdated();

                return this.getChannel()
                    .flatMap(channel -> channel.getMessageById(deferredReply.getMessageId()))
                    .flatMap(message -> message.edit(response.getD4jEditSpec()));
            })
            .switchIfEmpty(
                this.getChannel()
                    .flatMap(response::getD4jCreateMono)
                    .checkpoint(FormatUtil.format("Response Processing{0}", this.getIdentifier().map(identifier -> ": " + identifier).orElse("")))
                    .onErrorResume(throwable -> this.getDiscordBot().handleUncaughtException(
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
                        }))
                    )
            )
            .then();
    }

    default Mono<Void> withChannel(Function<MessageChannel, Mono<Void>> messageChannel) {
        return this.getChannel().flatMap(messageChannel).then();
    }

    default Mono<Void> withEvent(Function<T, Mono<Void>> event) {
        return Mono.just(this.getEvent()).flatMap(event).then();
    }

}
