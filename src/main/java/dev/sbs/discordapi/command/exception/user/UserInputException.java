package dev.sbs.discordapi.command.exception.user;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.data.tuple.Triple;
import dev.sbs.discordapi.util.exception.DiscordException;

/**
 * {@link UserInputException UserInputExceptions} are thrown when a user provides missing or invalid data.
 */
public class UserInputException extends DiscordException {

    protected UserInputException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, ConcurrentList<Triple<String, String, Boolean>> fields, ConcurrentMap<String, Object> data) {
        super(message, cause, enableSuppression, writableStackTrace, fields, data);
    }

}
