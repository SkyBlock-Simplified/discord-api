package dev.sbs.discordapi.command.exception.permission;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.data.tuple.triple.Triple;

/**
 * {@link BotPermissionException BotPermissionExceptions} are thrown when the bot lacks permissions to continue.
 */
public final class BotPermissionException extends PermissionException {

    private BotPermissionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, ConcurrentList<Triple<String, String, Boolean>> fields, ConcurrentMap<String, Object> data) {
        super(message, cause, enableSuppression, writableStackTrace, fields, data);
    }

}
