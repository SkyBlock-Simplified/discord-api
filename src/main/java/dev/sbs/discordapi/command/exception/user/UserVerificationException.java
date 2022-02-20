package dev.sbs.discordapi.command.exception.user;

import dev.sbs.api.util.concurrent.ConcurrentList;
import dev.sbs.api.util.concurrent.ConcurrentMap;
import dev.sbs.api.util.tuple.Triple;
import dev.sbs.discordapi.util.exception.DiscordException;

/**
 * {@link UserVerificationException UserVerificationExceptions} are thrown when a user is not verified.
 */
public class UserVerificationException extends DiscordException {

    protected UserVerificationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, ConcurrentList<Triple<String, String, Boolean>> fields, ConcurrentMap<String, Object> data) {
        super(message, cause, enableSuppression, writableStackTrace, fields, data);
    }

}
