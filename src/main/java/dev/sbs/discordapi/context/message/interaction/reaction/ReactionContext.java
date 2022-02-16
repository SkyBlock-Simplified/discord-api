package dev.sbs.discordapi.context.message.interaction.reaction;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.message.interaction.UserInteractionContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.message.MessageEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.event.domain.message.ReactionRemoveEvent;

public interface ReactionContext extends UserInteractionContext<MessageEvent> {

    Emoji getEmoji();

    Type getType();

    default void removeReactions() {
        this.getMessage()
            .flatMap(message -> message.removeReactions(this.getEmoji().getD4jReaction()))
            .block();
    }

    default void removeUserReaction() {
        this.getMessage()
            .flatMap(message -> message.removeReaction(this.getEmoji().getD4jReaction(), this.getInteractUserId()))
            .block();
    }

    static ReactionContext ofAdd(DiscordBot discordBot, ReactionAddEvent event, Response cachedMessage, Emoji emoji) {
        return new ReactionAddContextImpl(discordBot, event, cachedMessage.getUniqueId(), emoji);
    }

    static ReactionContext ofRemove(DiscordBot discordBot, ReactionRemoveEvent event, Response cachedMessage, Emoji emoji) {
        return new ReactionRemoveContextImpl(discordBot, event, cachedMessage.getUniqueId(), emoji);
    }

    enum Type {

        ADD,
        REMOVE

    }

}
