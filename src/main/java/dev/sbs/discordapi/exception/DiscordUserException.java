package dev.sbs.discordapi.exception;

import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when the user generates a recoverable error.
 */
public class DiscordUserException extends DiscordException {

    /**
     * Constructs a new {@code DiscordUserException} with the given cause.
     *
     * @param cause the cause of this exception
     */
    public DiscordUserException(@NotNull Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code DiscordUserException} with the given message.
     *
     * @param message the detail message
     */
    public DiscordUserException(@NotNull String message) {
        super(message);
    }

    /**
     * Constructs a new {@code DiscordUserException} with the given message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public DiscordUserException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new {@code DiscordUserException} with a formatted message.
     *
     * @param message the format string
     * @param args the format arguments
     */
    public DiscordUserException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
    }

}
