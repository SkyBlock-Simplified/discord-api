package dev.sbs.discordapi.exception;

import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when the Discord layer is unable to progress.
 */
public class DiscordException extends RuntimeException {

    /**
     * Constructs a new {@code DiscordException} with the given cause.
     *
     * @param cause the cause of this exception
     */
    public DiscordException(@NotNull Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code DiscordException} with the given message.
     *
     * @param message the detail message
     */
    public DiscordException(@NotNull String message) {
        super(message);
    }

    /**
     * Constructs a new {@code DiscordException} with the given message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public DiscordException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new {@code DiscordException} with a formatted message.
     *
     * @param message the format string
     * @param args the format arguments
     */
    public DiscordException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
    }

}
