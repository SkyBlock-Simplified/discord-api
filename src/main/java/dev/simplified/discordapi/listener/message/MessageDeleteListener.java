package dev.simplified.discordapi.listener.message;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.handler.response.CachedResponse;
import dev.simplified.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.message.MessageDeleteEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;

/**
 * Listener for message delete events, tearing down the corresponding
 * {@link CachedResponse} entry (top-level or followup) and any eternal cold
 * record when a tracked message is deleted - so a deleted eternal message is
 * not resurrected by a later interaction. The teardown is message-keyed because
 * a rebooted eternal may be cold-only with no hot entry to resolve an id from.
 */
public class MessageDeleteListener extends DiscordListener<MessageDeleteEvent> {

    /**
     * Constructs a new {@code MessageDeleteListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public MessageDeleteListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public final @NotNull Publisher<Void> apply(@NotNull MessageDeleteEvent event) {
        return this.getDiscordBot()
            .getResponseLocator()
            .deleteByMessage(event.getMessageId());
    }

}
