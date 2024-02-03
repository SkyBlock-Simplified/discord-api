package dev.sbs.discordapi.command.exception.user;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.collection.concurrent.ConcurrentMap;
import dev.sbs.api.mutable.triple.Triple;
import dev.sbs.discordapi.command.exception.CommandException;

/**
 * {@link UserVerificationException UserVerificationExceptions} are thrown when a user is not verified.
 */
public class UserVerificationException extends CommandException {

    protected UserVerificationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, ConcurrentList<Triple<String, String, Boolean>> fields, ConcurrentMap<String, Object> data) {
        super(message, cause, enableSuppression, writableStackTrace, fields, data);
    }

}
