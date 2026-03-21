package dev.sbs.discordapi.exception;

import dev.sbs.discordapi.DiscordBot;
import discord4j.core.DiscordClient;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when the {@link DiscordBot} cannot log in to the {@link DiscordClient}.
 */
public class DiscordClientException extends DiscordException {

    /**
     * Constructs a new {@code DiscordClientException} with the given cause.
     *
     * @param cause the cause of this exception
     */
    public DiscordClientException(@NotNull Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code DiscordClientException} with the given message.
     *
     * @param message the detail message
     */
    public DiscordClientException(@NotNull String message) {
        super(message);
    }

    /**
     * Constructs a new {@code DiscordClientException} with the given message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public DiscordClientException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new {@code DiscordClientException} with a formatted message.
     *
     * @param message the format string
     * @param args the format arguments
     */
    public DiscordClientException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
    }

}
