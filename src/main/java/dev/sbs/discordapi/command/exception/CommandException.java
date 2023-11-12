package dev.sbs.discordapi.command.exception;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.data.tuple.Triple;
import dev.sbs.discordapi.command.impl.SlashCommand;
import dev.sbs.discordapi.util.exception.DiscordException;

/**
 * {@link CommandException CommandExceptions} are thrown when any class extending the
 * {@link SlashCommand} class is unable to complete.
 */
public class CommandException extends DiscordException {

    protected CommandException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, ConcurrentList<Triple<String, String, Boolean>> fields, ConcurrentMap<String, Object> data) {
        super(message, cause, enableSuppression, writableStackTrace, fields, data);
    }

}
