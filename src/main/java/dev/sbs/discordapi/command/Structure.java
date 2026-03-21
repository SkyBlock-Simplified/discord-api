package dev.sbs.discordapi.command;

import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Metadata annotation that defines the identity and behavior of a {@link DiscordCommand}.
 *
 * <p>
 * Every concrete {@link DiscordCommand} subclass must be annotated with this type.
 * The annotation specifies the command's name, description, permission requirements,
 * installation contexts, and other behavioral flags consumed at registration time.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Structure {

    /**
     * The display name of this command in Discord.
     *
     * <ul>
     *     <li>Must be 1-32 characters matching {@code [\\w-]}</li>
     * </ul>
     */
    @Pattern("^[\\w-]{1,32}$")
    @NotNull String name();

    /**
     * The description shown for slash commands in the Discord UI.
     *
     * <ul>
     *     <li>Must be 1-100 characters</li>
     *     <li>Leave empty for {@link DiscordCommand.Type#USER USER} and
     *         {@link DiscordCommand.Type#MESSAGE MESSAGE} commands</li>
     * </ul>
     */
    @Pattern("^.{1,100}$")
    @NotNull String description();

    /**
     * The parent subcommand grouping for slash commands.
     *
     * <ul>
     *     <li>Defaults to an empty parent (no nesting)</li>
     *     <li>Not applicable to {@link DiscordCommand.Type#USER USER} and
     *         {@link DiscordCommand.Type#MESSAGE MESSAGE} commands</li>
     * </ul>
     */
    @SuppressWarnings("all")
    @NotNull Parent parent() default @Parent(name = "", description = "");

    /**
     * The subcommand group within a {@link #parent()} for slash commands.
     *
     * <ul>
     *     <li>Defaults to an empty group (no grouping)</li>
     *     <li>Not applicable to {@link DiscordCommand.Type#USER USER} and
     *         {@link DiscordCommand.Type#MESSAGE MESSAGE} commands</li>
     * </ul>
     */
    @SuppressWarnings("all")
    @NotNull Group group() default @Group(name = "", description = "");

    /**
     * The guild to restrict this command to, or {@code -1} for a global command.
     */
    long guildId() default -1L;

    /**
     * Whether the command is age-restricted (NSFW).
     */
    boolean nsfw() default false;

    /**
     * Whether only members of the application's developer team can invoke this command.
     */
    boolean developerOnly() default false;

    /**
     * Whether the command's initial reply is ephemeral (visible only to the invoker).
     */
    boolean ephemeral() default false;

    /**
     * Whether the command can only be executed by one user at a time.
     */
    boolean singleton() default false;

    /**
     * The permissions a user must have to see and invoke this command.
     *
     * <ul>
     *     <li>Defaults to no required permissions (everyone can invoke)</li>
     *     <li>Converted to a bit set using {@link PermissionSet#of}</li>
     * </ul>
     */
    @NotNull Permission[] userPermissions() default { };

    /**
     * The server/channel permissions the bot must have for this command to execute.
     *
     * <ul>
     *     <li>Defaults to no required permissions</li>
     *     <li>Converted to a bit set using {@link PermissionSet#of}</li>
     * </ul>
     */
    @NotNull Permission[] botPermissions() default { };

    /**
     * The installation contexts where this command is available.
     *
     * <ul>
     *     <li>Only applicable to globally-scoped commands</li>
     *     <li>Defaults to {@link DiscordCommand.Install#GUILD}</li>
     * </ul>
     */
    @NotNull DiscordCommand.Install[] integrations() default DiscordCommand.Install.GUILD;

    /**
     * The access contexts where this command can be invoked.
     *
     * <ul>
     *     <li>Only applicable to globally-scoped commands</li>
     *     <li>Defaults to all access types</li>
     * </ul>
     */
    @NotNull DiscordCommand.Access[] contexts() default { DiscordCommand.Access.GUILD, DiscordCommand.Access.DIRECT_MESSAGE, DiscordCommand.Access.PRIVATE_CHANNEL };

    /**
     * Metadata annotation defining the parent subcommand for a nested slash command.
     *
     * <p>
     * When a {@link DiscordCommand} is nested under a parent, the parent name and
     * description are used to register the top-level command group in Discord.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Parent {

        /**
         * The parent command name.
         *
         * <ul>
         *     <li>Must be 1-32 characters matching {@code [\\w-]}</li>
         * </ul>
         */
        @Pattern("^[\\w-]{1,32}$")
        @NotNull String name();

        /**
         * The parent command description shown in the Discord UI.
         *
         * <ul>
         *     <li>Must be 1-100 characters</li>
         * </ul>
         */
        @Pattern("^.{1,100}$")
        @NotNull String description();

    }

    /**
     * Metadata annotation defining the subcommand group within a {@link Parent}.
     *
     * <p>
     * Groups provide an additional level of nesting beneath a parent command,
     * organizing related subcommands together in the Discord UI.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Group {

        /**
         * The group name.
         *
         * <ul>
         *     <li>Must be 1-32 characters matching {@code [\\w-]}</li>
         * </ul>
         */
        @Pattern("^[\\w-]{1,32}$")
        @NotNull String name();

        /**
         * The group description shown in the Discord UI.
         *
         * <ul>
         *     <li>Must be 1-100 characters</li>
         * </ul>
         */
        @Pattern("^.{1,100}$")
        @NotNull String description();

    }

}
