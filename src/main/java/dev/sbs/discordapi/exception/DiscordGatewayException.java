package dev.sbs.discordapi.exception;

import dev.sbs.discordapi.DiscordBot;
import discord4j.core.GatewayDiscordClient;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link DiscordGatewayException DiscordGatewayExceptions} are thrown when the {@link DiscordBot} could not locate something in the {@link GatewayDiscordClient}.
 */
public class DiscordGatewayException extends DiscordException {

    public DiscordGatewayException(@NotNull Throwable cause) {
        super(cause);
    }

    public DiscordGatewayException(@NotNull String message) {
        super(message);
    }

    public DiscordGatewayException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
    }

}
