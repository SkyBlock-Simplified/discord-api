package dev.sbs.discordapi.command.exception.permission;

import dev.sbs.api.collection.concurrent.ConcurrentSet;
import dev.sbs.discordapi.context.deferrable.command.CommandContext;
import discord4j.rest.util.Permission;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * {@link BotPermissionException BotPermissionExceptions} are thrown when the bot lacks permissions to continue.
 */
@Getter
public class BotPermissionException extends PermissionException {

    private final @NotNull ConcurrentSet<Permission> requiredPermissions;

    public BotPermissionException(@NotNull CommandContext<?> commandContext, @NotNull ConcurrentSet<Permission> requiredPermissions) {
        super("The command '%s' lacks permissions required to run!", commandContext.getCommand().getName());
        this.requiredPermissions = requiredPermissions;
    }

}
