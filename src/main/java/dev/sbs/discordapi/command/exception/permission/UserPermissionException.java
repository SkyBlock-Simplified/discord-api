package dev.sbs.discordapi.command.exception.permission;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.collection.concurrent.ConcurrentMap;
import dev.sbs.api.mutable.triple.Triple;

/**
 * {@link UserPermissionException UserPermissionExceptions} are thrown when the user lacks permissions to continue.
 */
public final class UserPermissionException extends PermissionException {

    private UserPermissionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, ConcurrentList<Triple<String, String, Boolean>> fields, ConcurrentMap<String, Object> data) {
        super(message, cause, enableSuppression, writableStackTrace, fields, data);
    }

}
