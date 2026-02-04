package dev.sbs.discordapi.exception;

import dev.sbs.discordapi.DiscordBot;
import discord4j.core.DiscordClient;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link DiscordClientException DiscordClientExceptions} are thrown when the {@link DiscordBot} could not log in to the {@link DiscordClient}.
 */
public class DiscordClientException extends DiscordException {

    public DiscordClientException(@NotNull Throwable cause) {
        super(cause);
    }

    public DiscordClientException(@NotNull String message) {
        super(message);
    }

    public DiscordClientException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
    }

}
