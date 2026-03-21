package dev.sbs.discordapi.command.exception.permission;

/**
 * Thrown when a non-developer attempts to run a developer-only command.
 */
public class DeveloperPermissionException extends PermissionException {

    /**
     * Constructs a new {@code DeveloperPermissionException} with a default message.
     */
    public DeveloperPermissionException() {
        super("Only the bot developer can run this command.");
    }

}
