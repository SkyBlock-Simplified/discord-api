package dev.sbs.discordapi.command.data;

import dev.sbs.discordapi.command.Command;
import dev.sbs.discordapi.command.UserPermission;
import discord4j.rest.util.Permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the base settings of a discord command.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandInfo {

    /**
     * The id of the discord command. Used for database storage.
     *
     * @return The discord command id.
     */
    String id();

    /**
     * The name of the discord command.
     *
     * @return The discord command name.
     */
    String name();

    /**
     * The class of the parent command.
     *
     * @return Parent command class file
     */
    Class<? extends Command> parent() default Command.class;

    /**
     * Immutably required discord permissions required for the bot to process this command.
     *
     * @return An array of discord permissions.
     */
    Permission[] permissions() default { };

    /**
     * Immutably required user permissions required for users to use this command.
     *
     * @return The required user permissions.
     */
    UserPermission[] userPermissions() default { UserPermission.EVERYONE };

}
