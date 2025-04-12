package dev.sbs.discordapi.exception;

import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link DiscordUserException DiscordExceptions} are thrown when the user generates a recoverable error.
 */
public class DiscordUserException extends DiscordException {

    public DiscordUserException(@NotNull Throwable cause) {
        super(cause);
    }

    public DiscordUserException(@NotNull String message) {
        super(message);
    }

    public DiscordUserException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }

    public DiscordUserException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
    }

}
