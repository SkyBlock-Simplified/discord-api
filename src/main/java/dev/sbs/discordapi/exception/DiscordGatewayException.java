package dev.sbs.discordapi.exception;

import dev.sbs.discordapi.DiscordBot;
import discord4j.core.GatewayDiscordClient;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when the {@link DiscordBot} cannot connect to the {@link GatewayDiscordClient}.
 */
public class DiscordGatewayException extends DiscordException {

    /**
     * Constructs a new {@code DiscordGatewayException} with the given cause.
     *
     * @param cause the cause of this exception
     */
    public DiscordGatewayException(@NotNull Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code DiscordGatewayException} with the given message.
     *
     * @param message the detail message
     */
    public DiscordGatewayException(@NotNull String message) {
        super(message);
    }

    /**
     * Constructs a new {@code DiscordGatewayException} with the given message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public DiscordGatewayException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new {@code DiscordGatewayException} with a formatted message.
     *
     * @param message the format string
     * @param args the format arguments
     */
    public DiscordGatewayException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
    }

}
