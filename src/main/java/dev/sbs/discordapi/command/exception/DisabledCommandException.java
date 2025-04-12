package dev.sbs.discordapi.command.exception;

/**
 * {@link DisabledCommandException DiscordDisabledCommandExceptions} are thrown when a command is disabled and the bot cannot continue.
 */
public final class DisabledCommandException extends CommandException {

    public DisabledCommandException() {
        super("This command is currently disabled.");
    }

}
