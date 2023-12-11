package dev.sbs.discordapi.listener.message.reaction;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.reaction.ReactionContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class ReactionAddListener extends ReactionListener<ReactionAddEvent> {

    public ReactionAddListener(DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull ReactionContext getContext(@NotNull ReactionAddEvent event, @NotNull Response cachedMessage, @NotNull Emoji emoji, @NotNull Optional<Response.Cache.Followup> followup) {
        return ReactionContext.ofAdd(
            this.getDiscordBot(),
            event,
            cachedMessage,
            emoji,
            followup
        );
    }

    @Override
    protected @NotNull Snowflake getMessageId(@NotNull ReactionAddEvent event) {
        return event.getMessageId();
    }

    @Override
    protected @NotNull Emoji getEmoji(@NotNull ReactionAddEvent event) {
        return Emoji.of(event.getEmoji());
    }

    @Override
    protected @NotNull Snowflake getUserId(@NotNull ReactionAddEvent event) {
        return event.getUserId();
    }

    @Override
    protected boolean isBot(@NotNull ReactionAddEvent event) {
        return event.getUser().blockOptional().map(User::isBot).orElse(true);
    }

    @Override
    protected boolean isBotMessage(@NotNull ReactionAddEvent event) {
        return event.getMessage().blockOptional().flatMap(Message::getAuthor).map(User::isBot).orElse(false);
    }

}
