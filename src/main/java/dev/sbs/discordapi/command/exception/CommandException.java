package dev.sbs.discordapi.command.exception;

import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.exception.DiscordUserException;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when a {@link DiscordCommand} is unable to complete.
 */
public class CommandException extends DiscordUserException {

    /**
     * Constructs a new {@code CommandException} with the given cause.
     *
     * @param cause the cause of this exception
     */
    public CommandException(@NotNull Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code CommandException} with the given message.
     *
     * @param message the detail message
     */
    public CommandException(@NotNull String message) {
        super(message);
    }

    /**
     * Constructs a new {@code CommandException} with a formatted message.
     *
     * @param message the format string
     * @param args the format arguments
     */
    public CommandException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
    }

}
