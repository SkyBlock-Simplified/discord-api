package dev.sbs.discordapi.context;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.exception.ExceptionContext;
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

public interface EventContext<T extends Event> {

    default Mono<Message> discordBuildMessage(@NotNull Response response) {
        return this.getChannel()
            .flatMap(response::getD4jCreateMono)
            .publishOn(response.getReactorScheduler());
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
