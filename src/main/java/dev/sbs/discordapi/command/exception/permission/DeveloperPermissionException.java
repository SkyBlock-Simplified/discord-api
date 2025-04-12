package dev.sbs.discordapi.command.exception.permission;

/**
 * {@link DeveloperPermissionException UserPermissionExceptions} are thrown when the user lacks permissions to continue.
 */
public class DeveloperPermissionException extends PermissionException {

    public DeveloperPermissionException() {
        super("Only the bot developer can run this command.");
    }

}
