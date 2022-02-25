package dev.sbs.discordapi.command.exception;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.data.tuple.Triple;

/**
 * {@link HelpCommandException HelpCommandExceptions} are thrown when the user is looking for help with the current command.
 */
public final class HelpCommandException extends CommandException {

    private HelpCommandException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, ConcurrentList<Triple<String, String, Boolean>> fields, ConcurrentMap<String, Object> data) {
        super(message, cause, enableSuppression, writableStackTrace, fields, data);
    }

}
