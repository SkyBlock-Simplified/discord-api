package dev.sbs.discordapi.util.exception;

import dev.sbs.api.util.concurrent.ConcurrentList;
import dev.sbs.api.util.concurrent.ConcurrentMap;
import dev.sbs.api.util.tuple.Triple;

/**
 * {@link UserInputException UserInputExceptions} are thrown when a user provides missing or invalid data.
 */
public class UserInputException extends DiscordException {

    protected UserInputException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, ConcurrentList<Triple<String, String, Boolean>> fields, ConcurrentMap<String, Object> data) {
        super(message, cause, enableSuppression, writableStackTrace, fields, data);
    }

}
