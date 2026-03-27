package dev.sbs.discordapi.listener.message;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.message.ReactionContext;
import dev.sbs.discordapi.handler.response.ResponseFollowup;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.message.ReactionAddEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Listener for reaction add events, constructing a {@link ReactionContext} with
 * {@link ReactionContext.Type#ADD} and delegating to the emoji's interaction handler.
 */
public final class ReactionAddListener extends ReactionListener<ReactionAddEvent> {

    /**
     * Constructs a new {@code ReactionAddListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public ReactionAddListener(DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull ReactionContext getContext(@NotNull ReactionAddEvent event, @NotNull Response cachedMessage, @NotNull Emoji emoji, @NotNull Optional<ResponseFollowup> followup) {
        return ReactionContext.of(
            this.getDiscordBot(),
            event,
            cachedMessage,
            emoji,
            ReactionContext.Type.ADD,
            followup
        );
    }

}
