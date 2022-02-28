package dev.sbs.discordapi.context;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.exception.ExceptionContext;
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
import java.util.function.Function;

public interface EventContext<T extends Event> {

    Mono<MessageChannel> getChannel();

    Snowflake getChannelId();

    default GatewayDiscordClient getClient() {
        return this.getEvent().getClient();
    }

    DiscordBot getDiscordBot();

    T getEvent();

    Mono<Guild> getGuild();

    Optional<Snowflake> getGuildId();

    Mono<User> getInteractUser();

    Snowflake getInteractUserId();

    default Mono<PrivateChannel> getInteractUserPrivateChannel() {
        return this.getInteractUser().flatMap(User::getPrivateChannel);
    }

    default boolean isPrivateChannel() {
        return this.getGuildId().isEmpty();
    }

    default void reply(Response response) {
        this.softReply(response).block();
    }

    default Mono<Void> softReply(Response response) {
        return this.getChannel()
            .flatMap(response::getD4jCreateMono)
            .checkpoint("Response", true)
            .doOnError(throwable -> this.getDiscordBot().handleUncaughtException(
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

                    responseCacheEntry.updateLastInteract(); // Update TTL
                    responseCacheEntry.setUpdated();
                }))
            );
    }

    default Mono<Void> withChannel(Function<MessageChannel, Mono<Void>> messageChannel) {
        return this.getChannel().flatMap(messageChannel).then();
    }

    default Mono<Void> withEvent(Function<T, Mono<Void>> event) {
        return Mono.just(this.getEvent()).flatMap(event).then();
    }

}
