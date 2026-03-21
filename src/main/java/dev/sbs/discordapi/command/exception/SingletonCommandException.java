package dev.sbs.discordapi.command.exception;

/**
 * Thrown when a singleton command is already being executed by another user.
 */
public final class SingletonCommandException extends CommandException {

    /**
     * Constructs a new {@code SingletonCommandException} with a default message.
     */
    public SingletonCommandException() {
        super("This command is currently running.");
    }

}
