package dev.sbs.discordapi.command.exception;

import dev.sbs.api.collection.concurrent.ConcurrentSet;
import dev.sbs.discordapi.context.command.CommandContext;
import discord4j.rest.util.Permission;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Thrown when the bot lacks the required permissions to continue.
 */
@Getter
public class BotPermissionException extends PermissionException {

    /**
     * The set of permissions the bot requires but does not have.
     */
    private final @NotNull ConcurrentSet<Permission> requiredPermissions;

    /**
     * Constructs a new {@code BotPermissionException} with the given command context and required permissions.
     *
     * @param commandContext the context of the command that triggered this exception
     * @param requiredPermissions the set of permissions the bot is missing
     */
    public BotPermissionException(@NotNull CommandContext<?> commandContext, @NotNull ConcurrentSet<Permission> requiredPermissions) {
        super("The command '%s' lacks permissions required to run!", commandContext.getStructure().name());
        this.requiredPermissions = requiredPermissions;
    }

}
