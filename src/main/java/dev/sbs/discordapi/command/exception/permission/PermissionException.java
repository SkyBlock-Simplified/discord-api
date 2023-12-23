package dev.sbs.discordapi.command.exception.permission;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.mutable.tuple.triple.Triple;
import dev.sbs.discordapi.command.exception.CommandException;

/**
 * {@link PermissionException PermissionExceptions} are thrown when something lacks permissions to continue.
 */
public abstract class PermissionException extends CommandException {

    protected PermissionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, ConcurrentList<Triple<String, String, Boolean>> fields, ConcurrentMap<String, Object> data) {
        super(message, cause, enableSuppression, writableStackTrace, fields, data);
    }

}
