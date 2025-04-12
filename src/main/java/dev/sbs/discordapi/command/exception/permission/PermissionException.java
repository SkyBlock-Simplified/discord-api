package dev.sbs.discordapi.command.exception.permission;

import dev.sbs.discordapi.command.exception.CommandException;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PermissionException PermissionExceptions} are thrown when something lacks permissions to continue.
 */
public abstract class PermissionException extends CommandException {

    public PermissionException(@NotNull Throwable cause) {
        super(cause);
    }

    public PermissionException(@NotNull String message) {
        super(message);
    }

    public PermissionException(@NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
    }

}
