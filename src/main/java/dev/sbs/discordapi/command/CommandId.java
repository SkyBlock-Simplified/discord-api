package dev.sbs.discordapi.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
