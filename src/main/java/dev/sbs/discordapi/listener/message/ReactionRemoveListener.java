package dev.sbs.discordapi.listener.message;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.message.ReactionContext;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.message.ReactionRemoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Listener for reaction remove events, constructing a {@link ReactionContext} with
 * {@link ReactionContext.Type#REMOVE} and delegating to the emoji's interaction handler.
 */
public final class ReactionRemoveListener extends ReactionListener<ReactionRemoveEvent> {

    /**
     * Constructs a new {@code ReactionRemoveListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public ReactionRemoveListener(DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull ReactionContext getContext(@NotNull ReactionRemoveEvent event, @NotNull Response cachedMessage, @NotNull Emoji emoji, @NotNull Optional<Followup> followup) {
        return ReactionContext.of(
            this.getDiscordBot(),
            event,
            cachedMessage,
            emoji,
            ReactionContext.Type.REMOVE,
            followup
        );
    }

}
