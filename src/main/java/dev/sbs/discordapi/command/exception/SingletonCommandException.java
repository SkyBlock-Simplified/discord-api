package dev.sbs.discordapi.command.exception;

/**
 * {@link SingletonCommandException SingletonCommandExceptions} are thrown when a command has already been executed by another user.
 */
public final class SingletonCommandException extends CommandException {

    public SingletonCommandException() {
        super("This command is currently running.");
    }

}
