package dev.sbs.discordapi.context.message.reaction;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.response.Emoji;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
class ReactionAddContextImpl implements ReactionContext {

    @Getter private final DiscordBot discordBot;
    @Getter private final ReactionAddEvent event;
    @Getter private final UUID responseId;
    @Getter private final Emoji emoji;
    @Getter private final Type type = Type.ADD;

    @Override
    public @NotNull Snowflake getChannelId() {
        return this.getEvent().getChannelId();
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
    public @NotNull User getInteractUser() {
        return Objects.requireNonNull(this.getEvent().getUser().block());
    }

    @Override
    public @NotNull Snowflake getInteractUserId() {
        return this.getEvent().getUserId();
    }

    @Override
    public Mono<Message> getMessage() {
        return this.getEvent().getMessage();
    }

    @Override
    public Snowflake getMessageId() {
        return this.getEvent().getMessageId();
    }

}
