package dev.sbs.discordapi.listener.message.reaction;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.reaction.ReactionContext;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.message.ReactionRemoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class ReactionRemoveListener extends ReactionListener<ReactionRemoveEvent> {

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
