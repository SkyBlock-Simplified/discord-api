package dev.sbs.discordapi.listener.message.reaction;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.reaction.ReactionContext;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.message.ReactionAddEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class ReactionAddListener extends ReactionListener<ReactionAddEvent> {

    public ReactionAddListener(DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull ReactionContext getContext(@NotNull ReactionAddEvent event, @NotNull Response cachedMessage, @NotNull Emoji emoji, @NotNull Optional<Followup> followup) {
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
