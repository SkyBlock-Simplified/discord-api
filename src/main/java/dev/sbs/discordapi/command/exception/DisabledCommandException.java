package dev.sbs.discordapi.command.exception;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.data.tuple.Triple;

/**
 * {@link DisabledCommandException DiscordDisabledCommandExceptions} are thrown when the bot lacks permissions to continue.
 */
public final class DisabledCommandException extends CommandException {

    private DisabledCommandException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, ConcurrentList<Triple<String, String, Boolean>> fields, ConcurrentMap<String, Object> data) {
        super(message, cause, enableSuppression, writableStackTrace, fields, data);
    }

}
