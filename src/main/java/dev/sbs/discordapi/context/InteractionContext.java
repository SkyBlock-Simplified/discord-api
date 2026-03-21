package dev.sbs.discordapi.context;

import dev.sbs.discordapi.context.command.AutoCompleteContext;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Specialization of {@link EventContext} for Discord interaction events, delegating
 * channel, guild, and user resolution to the underlying
 * {@link InteractionCreateEvent#getInteraction() Interaction} object.
 *
 * <p>
 * This interface sits between the root {@link EventContext} and the more specific
 * interaction subtypes:
 * <ul>
 *   <li><b>{@link AutoCompleteContext}</b> - autocomplete suggestion events</li>
 *   <li><b>{@link DeferrableInteractionContext}</b> - interactions that support deferred replies
 *       (commands and components)</li>
 * </ul>
 *
 * @param <T> the Discord4J {@link InteractionCreateEvent} subtype wrapped by this context
 */
public interface InteractionContext<T extends InteractionCreateEvent> extends EventContext<T> {

    /** {@inheritDoc} */
    @Override
    default Mono<MessageChannel> getChannel() {
        return this.getEvent().getInteraction().getChannel();
    }

    /** {@inheritDoc} */
    @Override
    default @NotNull Snowflake getChannelId() {
        return this.getEvent().getInteraction().getChannelId();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Resolves the guild via the bot's gateway client rather than the interaction object,
     * because {@code ChatInputInteractionEvent#getInteraction().getGuild()} may return empty.
     */
    @Override
    default Mono<Guild> getGuild() {
        // Guild in ChatInputInteractionEvent#getInteraction Empty
        return Mono.justOrEmpty(this.getGuildId()).flatMap(guildId -> this.getDiscordBot().getGateway().getGuildById(guildId));
    }

    /** {@inheritDoc} */
    @Override
    default Optional<Snowflake> getGuildId() {
        return this.getEvent().getInteraction().getGuildId();
    }

    /** {@inheritDoc} */
    @Override
    default @NotNull User getInteractUser() {
        return this.getEvent().getInteraction().getUser();
    }

    /** {@inheritDoc} */
    @Override
    default @NotNull Snowflake getInteractUserId() {
        return this.getEvent().getInteraction().getUser().getId();
    }

}
