package dev.sbs.discordapi.command.impl;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.reference.MessageCommandReference;
import dev.sbs.discordapi.context.interaction.deferrable.application.MessageCommandContext;
import dev.sbs.discordapi.response.Emoji;
import discord4j.core.event.domain.interaction.MessageInteractionEvent;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Getter
public abstract class MessageCommand extends DiscordCommand<MessageInteractionEvent, MessageCommandContext> implements MessageCommandReference {

    private @NotNull Optional<Emoji> emoji = Optional.empty();

    protected MessageCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

}
