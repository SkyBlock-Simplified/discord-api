package dev.sbs.discordapi.command.exception.user;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.mutable.tuple.triple.Triple;
import dev.sbs.discordapi.command.exception.CommandException;

/**
 * {@link UserInputException UserInputExceptions} are thrown when a user provides missing or invalid data.
 */
public class UserInputException extends CommandException {

    protected UserInputException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, ConcurrentList<Triple<String, String, Boolean>> fields, ConcurrentMap<String, Object> data) {
        super(message, cause, enableSuppression, writableStackTrace, fields, data);
    }

}
