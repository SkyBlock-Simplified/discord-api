package dev.sbs.discordapi.command;

import dev.sbs.discordapi.command.context.AccessContext;
import dev.sbs.discordapi.command.context.InstallContext;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Immutable {@link DiscordCommand} api structure.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandStructure {

    /**
     * Name of command.
     * <ul>
     *     <li>1-32 Characters</li>
     * </ul>
     */
    @Pattern("^[\\w-]{1,32}$")
    @NotNull String name();

    /**
     * Description for slash commands.
     * <ul>
     *     <li>1-100 Characters</li>
     *     <li>Empty for USER and MESSAGE commands</li>
     * </ul>
     */
    @Pattern("^.{1,100}$")
    @NotNull String description() default "";

    /**
     * Parent data of slash command.
     * <ul>
     *     <li>Empty for USER and MESSAGE commands</li>
     * </ul>
     */
    @SuppressWarnings("all")
    @NotNull Parent parent() default @Parent(name = "", description = "");

    /**
     * Group data of slash command.
     * <ul>
     *     <li>Empty for USER and MESSAGE commands</li>
     * </ul>
     */
    @SuppressWarnings("all")
    @NotNull Group group() default @Group(name = "", description = "");

    /**
     * Guild ID of the command.
     *
     * <ul>
     *     <li>Defaults to -1 for Global</li>
     * </ul>
     */
    long guildId() default -1L;

    /**
     * Indicates whether the command is age-restricted.
     * <ul>
     *     <li>Defaults to false</li>
     * </ul>
     */
    boolean nsfw() default false;

    /**
     * Only members of the developer team can run this command.
     * <ul>
     *     <li>Defaults to false</li>
     * </ul>
     */
    boolean developerOnly() default false;

    /**
     * Indicates whether the command will respond ephemerally.
     * <ul>
     *     <li>Defaults to false</li>
     * </ul>
     */
    boolean ephemeral() default false;

    /**
     * Users with these permissions can initially run this command.
     *
     * <ul>
     *     <li>Defaults to everyone</li>
     *     <li>Converted to a bit set using {@link PermissionSet#of}</li>
     * </ul>
     */
    @NotNull Permission[] userPermissions() default { };

    /**
     * Server/Channel permissions required for this command to run.
     *
     * <ul>
     *     <li>Defaults to nothing</li>
     *     <li>Converted to a bit set using {@link PermissionSet#of}</li>
     * </ul>
     */
    @NotNull Permission[] botPermissions() default { };

    /**
     * Installation contexts where the command is available.
     *
     * <ul>
     *     <li>Only for globally-scoped commands</li>
     *     <li>Defaults to Guild context</li>
     * </ul>
     */
    @NotNull InstallContext[] integrations() default InstallContext.GUILD;

    /**
     * Access contexts where the command can be used.
     *
     * <ul>
     *     <li>Only for globally-scoped commands</li>
     *     <li>Defaults to all access types for new commands</li>
     * </ul>
     */
    @NotNull AccessContext[] contexts() default { AccessContext.GUILD, AccessContext.DIRECT_MESSAGE, AccessContext.PRIVATE_CHANNEL };

    /**
     * Immutable {@link DiscordCommand} parent api structure.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Parent {

        /**
         * Parent name of command.
         * <ul>
         *     <li>1-32 Characters</li>
         * </ul>
         */
        @Pattern("^[\\w-]{1,32}$")
        @NotNull String name();

        /**
         * Parent description of command.
         * <ul>
         *     <li>1-100 Characters</li>
         * </ul>
         */
        @Pattern("^.{1,100}$")
        @NotNull String description();

    }

    /**
     * Immutable {@link DiscordCommand} group api structure.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Group {

        /**
         * Group name of command.
         * <ul>
         *     <li>1-32 Characters</li>
         * </ul>
         */
        @Pattern("^[\\w-]{1,32}$")
        @NotNull String name();

        /**
         * Group description of command.
         * <ul>
         *     <li>1-100 Characters</li>
         * </ul>
         */
        @Pattern("^.{1,100}$")
        @NotNull String description();

    }

}
