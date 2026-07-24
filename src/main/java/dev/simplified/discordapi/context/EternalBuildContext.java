package dev.simplified.discordapi.context;

import dev.simplified.discordapi.DiscordBot;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.Event;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * Lightweight {@link EventContext} handed to an
 * {@link dev.simplified.discordapi.listener.Eternal @Eternal} builder while it reconstructs a
 * {@link dev.simplified.discordapi.response.Response Response} - at creation time, at hydration time
 * after a restart, and during a scheduled/event refresh.
 *
 * <p>
 * It deliberately does not extend {@link dev.simplified.discordapi.context.scope.MessageContext
 * MessageContext}, so a builder has no {@code getResponse()} to call - there is no cached response
 * to read while one is being built. It carries only what a builder needs: the bot, the originating
 * channel/guild/user, the stable response id being assigned, and the opaque
 * {@link #getPayload() payload} the builder itself produced at creation.
 *
 * <p>
 * A refresh build has no triggering event ({@link #ofRefresh}); deterministic builders should rely
 * on {@link #getPayload()}/{@link #getGuildId()} rather than {@link #getEvent()}, which throws for
 * such contexts.
 *
 * @see dev.simplified.discordapi.listener.Eternal
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class EternalBuildContext implements EventContext<Event> {

    /** The bot instance reconstructing the response. */
    private final @NotNull DiscordBot discordBot;

    /** The event that triggered the build, or {@code null} for a refresh build. */
    @Getter(AccessLevel.NONE)
    private final @Nullable Event event;

    /** The Discord channel snowflake the eternal message lives in. */
    private final @NotNull Snowflake channelId;

    /** The Discord guild snowflake, or empty for direct-message contexts. */
    private final @NotNull Optional<Snowflake> guildId;

    /** The user associated with the eternal message. */
    private final @NotNull User interactUser;

    /** The stable response id being assigned to the response under construction. */
    private final @NotNull UUID responseId;

    /** The opaque builder payload captured at creation and replayed at hydration. */
    private final @NotNull String payload;

    /**
     * Creates a build context tied to a triggering event (creation or hydration).
     *
     * @param discordBot the bot instance
     * @param event the triggering event (creation or interaction)
     * @param channelId the channel snowflake of the eternal message
     * @param guildId the guild snowflake, or empty in a direct message
     * @param interactUser the user associated with the message
     * @param responseId the stable response id being assigned
     * @param payload the opaque builder payload
     * @return a new build context
     */
    public static @NotNull EternalBuildContext of(
        @NotNull DiscordBot discordBot,
        @NotNull Event event,
        @NotNull Snowflake channelId,
        @NotNull Optional<Snowflake> guildId,
        @NotNull User interactUser,
        @NotNull UUID responseId,
        @NotNull String payload
    ) {
        return new EternalBuildContext(discordBot, event, channelId, guildId, interactUser, responseId, payload);
    }

    /**
     * Creates an event-less build context for a refresh. {@link #getEvent()} throws on such a
     * context, so only deterministic builders (relying on the payload) can be refreshed this way.
     *
     * @param discordBot the bot instance
     * @param channelId the channel snowflake of the eternal message
     * @param guildId the guild snowflake, or empty in a direct message
     * @param interactUser the user associated with the message
     * @param responseId the stable response id being assigned
     * @param payload the opaque builder payload
     * @return a new refresh build context
     */
    public static @NotNull EternalBuildContext ofRefresh(
        @NotNull DiscordBot discordBot,
        @NotNull Snowflake channelId,
        @NotNull Optional<Snowflake> guildId,
        @NotNull User interactUser,
        @NotNull UUID responseId,
        @NotNull String payload
    ) {
        return new EternalBuildContext(discordBot, null, channelId, guildId, interactUser, responseId, payload);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException when this is a refresh build with no triggering event
     */
    @Override
    public @NotNull Event getEvent() {
        if (this.event == null)
            throw new IllegalStateException(
                "No triggering event: this eternal build is a refresh - deterministic builders should rely on getPayload()/getGuildId(), not getEvent()"
            );

        return this.event;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<MessageChannel> getChannel() {
        return this.discordBot.getGateway()
            .getChannelById(this.channelId)
            .ofType(MessageChannel.class);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Guild> getGuild() {
        return this.guildId
            .map(id -> this.discordBot.getGateway().getGuildById(id))
            .orElseGet(Mono::empty);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Snowflake getInteractUserId() {
        return this.interactUser.getId();
    }

}
