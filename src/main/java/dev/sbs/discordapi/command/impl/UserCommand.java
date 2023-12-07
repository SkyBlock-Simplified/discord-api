package dev.sbs.discordapi.command.impl;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.reference.UserCommandReference;
import dev.sbs.discordapi.context.interaction.deferrable.application.UserCommandContext;
import dev.sbs.discordapi.response.Emoji;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Getter
public abstract class UserCommand extends DiscordCommand<UserCommandContext> implements UserCommandReference {

    private @NotNull Optional<Emoji> emoji = Optional.empty();

    protected UserCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

}
