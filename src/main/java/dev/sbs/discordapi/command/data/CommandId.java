package dev.sbs.discordapi.command.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the base settings of a discord command.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandId {

    /**
     * The id of the discord command. Used for database storage.
     *
     * @return The discord command id.
     */
    String value();

}
