package dev.sbs.discordapi.util.exception;

import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link DiscordException DiscordExceptions} are thrown when something is unable to progress.
 */
public class DiscordException extends RuntimeException {

    public DiscordException(@NotNull Throwable cause) {
        super(cause);
    }

    public DiscordException(@NotNull String message) {
        super(message);
    }

    public DiscordException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }

    public DiscordException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
    }

}
