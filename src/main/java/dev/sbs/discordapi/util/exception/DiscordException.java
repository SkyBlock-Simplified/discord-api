package dev.sbs.discordapi.util.exception;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.collection.concurrent.ConcurrentMap;
import dev.sbs.api.mutable.triple.Triple;
import dev.sbs.api.util.SimplifiedException;

/**
 * {@link DiscordException DiscordExceptions} are thrown when something is unable to progress.
 */
public class DiscordException extends SimplifiedException {

    protected DiscordException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, ConcurrentList<Triple<String, String, Boolean>> fields, ConcurrentMap<String, Object> data) {
        super(message, cause, enableSuppression, writableStackTrace, fields, data);
    }

}
