package dev.sbs.discordapi.command;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.context.TypeContext;
import dev.sbs.discordapi.context.deferrable.command.MessageCommandContext;
import org.jetbrains.annotations.NotNull;

public abstract class MessageCommand extends DiscordCommand<MessageCommandContext> {

    protected MessageCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public final @NotNull TypeContext getType() {
        return TypeContext.MESSAGE;
    }

}
