package dev.sbs.discordapi.context;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;

public interface InteractionContext<T extends InteractionCreateEvent> extends EventContext<T> {

    @Override
    default Mono<MessageChannel> getChannel() {
        return this.getEvent().getInteraction().getChannel();
    }

    @Override
    default @NotNull Snowflake getChannelId() {
        return this.getEvent().getInteraction().getChannelId();
    }

    @Override
    default Mono<Guild> getGuild() {
        // Guild in ChatInputInteractionEvent#getInteraction Empty
        return Mono.justOrEmpty(this.getGuildId()).flatMap(guildId -> this.getDiscordBot().getGateway().getGuildById(guildId));
    }

    @Override
    default Optional<Snowflake> getGuildId() {
        return this.getEvent().getInteraction().getGuildId();
    }

    @Override
    default @NotNull User getInteractUser() {
        return this.getEvent().getInteraction().getUser();
    }

    @Override
    default @NotNull Snowflake getInteractUserId() {
        return this.getEvent().getInteraction().getUser().getId();
    }

}
