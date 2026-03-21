package dev.sbs.discordapi.command.exception;

/**
 * Thrown when a command is disabled and the bot cannot continue.
 */
public final class DisabledCommandException extends CommandException {

    /**
     * Constructs a new {@code DisabledCommandException} with a default message.
     */
    public DisabledCommandException() {
        super("This command is currently disabled.");
    }

}
