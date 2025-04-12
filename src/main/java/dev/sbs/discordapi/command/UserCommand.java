package dev.sbs.discordapi.command;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.context.TypeContext;
import dev.sbs.discordapi.context.deferrable.command.UserCommandContext;
import org.jetbrains.annotations.NotNull;

public abstract class UserCommand extends DiscordCommand<UserCommandContext> {

    protected UserCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public final @NotNull TypeContext getType() {
        return TypeContext.USER;
    }

}
