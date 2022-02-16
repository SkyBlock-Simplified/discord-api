package dev.sbs.discordapi.command.data;

import dev.sbs.discordapi.command.Category;
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
     * The aliases of the discord command.
     *
     * @return An array of regex discord command aliases.
     */
    String[] aliases() default { };

    /**
     * Gets the emoji snowflake id.
     * <br><br>
     * Use -1 to disable.<br>
     * Use 0 for unicode.
     *
     * @return Emoji id.
     */
    long emojiId() default -1;

    /**
     * Gets the emoji name.
     *
     * @return Emoji name.
     */
    String emojiName() default "";

    /**
     * Gets if the emoji is animated.
     *
     * @return True if animated.
     */
    boolean emojiAnimated() default false;

    /**
     * Gets if the command is enabled.
     *
     * @return True if enabled.
     */
    boolean enabled() default true;

    /**
     * Gets the description.
     *
     * @return Description of the command.
     */
    String description() default "";

    /**
     * The command group of the discord command.
     *
     * @return The slash command group of the parent command.
     */
    String group() default "";

    /**
     * Gets the guild this command is locked to.
     * <br><br>
     * Use -1 to disable.
     *
     * @return True if enabled
     */
    long guildId() default -1;

    /**
     * Gets the long description.
     *
     * @return Description of the command.
     */
    String longDescription() default "";

    /**
     * Whether to inherit parent command permissions.
     *
     * @return True to inherit parent permissions.
     */
    boolean inherit() default true;

    /**
     * The name of the discord command.
     *
     * @return The discord command name.
     */
    String name();

    /**
     * The category this command belongs to.
     *
     * @return The command category.
     */
    Category category() default Category.UNCATEGORIZED;

    /**
     * The class of the parent command.
     *
     * @return Parent command class file
     */
    Class<? extends Command> parent() default Command.class;

    /**
     * What discord permissions are required for the bot to use this command.
     *
     * @return An array of discord permissions.
     */
    Permission[] permissions() default { };

    /**
     * What user permissions are required to use this command.
     *
     * @return The required user permissions.
     */
    UserPermission[] userPermissions() default { UserPermission.NONE };

}
