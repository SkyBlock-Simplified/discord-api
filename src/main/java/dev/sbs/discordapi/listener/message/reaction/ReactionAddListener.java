package dev.sbs.discordapi.listener.message.reaction;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.message.interaction.reaction.ReactionContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;

public final class ReactionAddListener extends ReactionListener<ReactionAddEvent> {

    public ReactionAddListener(DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected ReactionContext getContext(ReactionAddEvent event, Response cachedMessage, Emoji emoji) {
        return ReactionContext.ofAdd(this.getDiscordBot(), event, cachedMessage, emoji);
    }

    @Override
    protected Snowflake getMessageId(ReactionAddEvent event) {
        return event.getMessageId();
    }

    @Override
    protected Emoji getEmoji(ReactionAddEvent event) {
        return Emoji.of(event.getEmoji());
    }

    @Override
    protected Snowflake getUserId(ReactionAddEvent event) {
        return event.getUserId();
    }

    @Override
    protected boolean isBot(ReactionAddEvent event) {
        return event.getUser().blockOptional().map(User::isBot).orElse(true);
    }

    @Override
    protected boolean isBotMessage(ReactionAddEvent event) {
        return event.getMessage().blockOptional().flatMap(Message::getAuthor).map(User::isBot).orElse(false);
    }

}
