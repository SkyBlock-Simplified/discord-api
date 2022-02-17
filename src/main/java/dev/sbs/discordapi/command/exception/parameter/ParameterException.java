package dev.sbs.discordapi.command.exception.parameter;

import dev.sbs.api.util.concurrent.ConcurrentList;
import dev.sbs.api.util.concurrent.ConcurrentMap;
import dev.sbs.api.util.tuple.Triple;
import dev.sbs.discordapi.command.exception.CommandException;

/**
 * {@link ParameterException ParameterExceptions} are thrown when the user passes invalid arguments to a command.
 */
public abstract class ParameterException extends CommandException {

    protected ParameterException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, ConcurrentList<Triple<String, String, Boolean>> fields, ConcurrentMap<String, Object> data) {
        super(message, cause, enableSuppression, writableStackTrace, fields, data);
    }

}