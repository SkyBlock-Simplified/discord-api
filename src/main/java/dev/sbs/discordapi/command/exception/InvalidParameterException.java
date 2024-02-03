package dev.sbs.discordapi.command.exception;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.collection.concurrent.ConcurrentMap;
import dev.sbs.api.mutable.triple.Triple;

/**
 * {@link InvalidParameterException InvalidParameterExceptions} are thrown when the user passes invalid arguments to a command.
 */
public final class InvalidParameterException extends CommandException {

    private InvalidParameterException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, ConcurrentList<Triple<String, String, Boolean>> fields, ConcurrentMap<String, Object> data) {
        super(message, cause, enableSuppression, writableStackTrace, fields, data);
    }

}
