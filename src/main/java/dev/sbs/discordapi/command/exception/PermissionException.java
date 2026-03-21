package dev.sbs.discordapi.command.exception;

import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when something lacks the required permissions to continue.
 */
public abstract class PermissionException extends CommandException {

    /**
     * Constructs a new {@code PermissionException} with the given cause.
     *
     * @param cause the cause of this exception
     */
    public PermissionException(@NotNull Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code PermissionException} with the given message.
     *
     * @param message the detail message
     */
    public PermissionException(@NotNull String message) {
        super(message);
    }

    /**
     * Constructs a new {@code PermissionException} with a formatted message.
     *
     * @param message the format string
     * @param args the format arguments
     */
    public PermissionException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
    }

}
