package dev.sbs.discordapi.command.exception;

import dev.sbs.discordapi.command.impl.DiscordCommand;
import dev.sbs.discordapi.util.exception.DiscordUserException;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link CommandException CommandExceptions} are thrown when a {@link DiscordCommand} is unable to complete.
 */
public class CommandException extends DiscordUserException {

    public CommandException(@NotNull Throwable cause) {
        super(cause);
    }

    public CommandException(@NotNull String message) {
        super(message);
    }

    public CommandException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
    }

}
