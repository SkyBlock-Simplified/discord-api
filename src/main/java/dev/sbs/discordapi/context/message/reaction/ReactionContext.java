package dev.sbs.discordapi.context.message.reaction;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.ResponseContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.message.MessageEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.event.domain.message.ReactionRemoveEvent;
import reactor.core.publisher.Mono;

public interface ReactionContext extends ResponseContext<MessageEvent> {

    Emoji getEmoji();

    Type getType();

    default Mono<Void> removeReactions() {
        return this.getMessage().flatMap(message -> message.removeReactions(this.getEmoji().getD4jReaction()));
    }

    default Mono<Void> removeUserReaction() {
        return this.getMessage().flatMap(message -> message.removeReaction(this.getEmoji().getD4jReaction(), this.getInteractUserId()));
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
