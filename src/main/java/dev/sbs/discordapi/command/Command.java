package dev.sbs.discordapi.command;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.data.model.discord.command_data.command_categories.CommandCategoryModel;
import dev.sbs.api.data.model.discord.command_data.command_configs.CommandConfigModel;
import dev.sbs.api.data.model.discord.command_data.command_groups.CommandGroupModel;
import dev.sbs.api.data.model.discord.command_data.command_parents.CommandParentModel;
import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentSet;
import dev.sbs.api.util.collection.concurrent.unmodifiable.ConcurrentUnmodifiableList;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.data.Argument;
import dev.sbs.discordapi.command.data.CommandData;
import dev.sbs.discordapi.command.data.CommandId;
import dev.sbs.discordapi.command.data.Parameter;
import dev.sbs.discordapi.command.exception.DisabledCommandException;
import dev.sbs.discordapi.command.exception.parameter.InvalidParameterException;
import dev.sbs.discordapi.command.exception.parameter.MissingParameterException;
import dev.sbs.discordapi.command.exception.permission.BotPermissionException;
import dev.sbs.discordapi.command.exception.permission.UserPermissionException;
import dev.sbs.discordapi.command.relationship.Relationship;
import dev.sbs.discordapi.context.CommandContext;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.util.base.DiscordHelper;
import dev.sbs.discordapi.util.exception.DiscordException;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.rest.util.Permission;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public abstract class Command extends DiscordHelper implements CommandData, Function<CommandContext<?>, Mono<Void>> {

    private static final ConcurrentUnmodifiableList<Parameter> NO_PARAMETERS = Concurrent.newUnmodifiableList();
    private static final ConcurrentUnmodifiableList<String> NO_EXAMPLES = Concurrent.newUnmodifiableList();
    private final static ConcurrentList<String> helpArguments = Concurrent.newUnmodifiableList("help", "?");
    @Getter private final @NotNull DiscordBot discordBot;
    @Getter private final @NotNull UUID uniqueId;

    protected Command(@NotNull DiscordBot discordBot) {
        super(discordBot);
        this.discordBot = discordBot;
        this.uniqueId = getCommandAnnotation(this.getClass())
            .map(CommandId::value)
            .map(StringUtil::toUUID)
            .orElseThrow(); // Will Never Throw
    }

    public final @NotNull Optional<CommandCategoryModel> getCategory() {
        return Optional.ofNullable(this.getConfig().getCategory());
    }

    public final @NotNull CommandConfigModel getConfig() {
        return SimplifiedApi.getRepositoryOf(CommandConfigModel.class).findFirstOrNull(CommandConfigModel::getUniqueId, this.getUniqueId());
    }

    public final @NotNull String getCommandPath(boolean slashCommand) {
        // Get Root Command Prefix
        String rootCommand = this.getDiscordBot()
            .getRootCommandRelationship()
            .getName();

        return FormatUtil.format("{0}{1}", (slashCommand ? "/" : rootCommand + " "), super.getCommandPath(this)).trim();
    }

    public final @NotNull String getDescription() {
        return this.getConfig().getDescription();
    }

    public final @NotNull Optional<CommandGroupModel> getGroup() {
        return Optional.ofNullable(this.getConfig().getGroup());
    }

    public final @NotNull Optional<String> getLongDescription() {
        return Optional.ofNullable(this.getConfig().getLongDescription());
    }

    public final @NotNull ConcurrentList<Relationship> getParentCommands() {
        ConcurrentList<Relationship> parentCommands = Concurrent.newList();
        this.getParentRelationship().ifPresent(parentCommands::add);

        // Handle Prefix Command
        if (this.getDiscordBot().getCommandRegistrar().getPrefix().isPresent())
            parentCommands.add(this.getDiscordBot().getRootCommandRelationship());

        return Concurrent.newUnmodifiableList(parentCommands.inverse());
    }

    public final @NotNull ConcurrentList<String> getParentCommandNames() {
        return this.getParentCommands()
            .stream()
            .map(Relationship::getName)
            .collect(Concurrent.toList());
    }

    public final @NotNull Optional<Relationship.Parent> getParentRelationship() {
        return this.getCompactedRelationships()
            .stream()
            .filter(Relationship.Parent.class::isInstance)
            .map(Relationship.Parent.class::cast)
            .filter(relationship -> relationship.getValue().getKey().equals(Optional.ofNullable(
                this.getConfig().getParent()).map(CommandParentModel::getKey).orElse("")
            ))
            .findFirst();
    }

    public final @NotNull Relationship.Command getRelationship() {
        return this.getCompactedRelationships()
            .stream()
            .filter(Relationship.Command.class::isInstance)
            .map(Relationship.Command.class::cast)
            .filter(relationship -> relationship.getUniqueId().equals(this.getConfig().getUniqueId()))
            .findFirst()
            .orElseThrow();
    }

    public final @NotNull Optional<Emoji> getEmoji() {
        return Emoji.of(this.getConfig().getEmoji());
    }

    public @NotNull ConcurrentUnmodifiableList<String> getExampleArguments() {
        return NO_EXAMPLES;
    }

    public final @NotNull Optional<Parameter> getParameter(int index) {
        ConcurrentList<Parameter> parameters = this.getParameters();
        index = Math.max(0, index);
        return Optional.ofNullable(index < parameters.size() ? parameters.get(index) : null);
    }

    public @NotNull ConcurrentUnmodifiableList<Parameter> getParameters() {
        return NO_PARAMETERS;
    }

    /**
     * Immutable set of {@link Permission discord permissions} required for the bot to process this command.
     *
     * @return The required discord permissions.
     */
    @Override
    public @NotNull ConcurrentSet<Permission> getPermissions() {
        return Concurrent.newUnmodifiableSet();
    }

    /**
     * Immutable set of {@link UserPermission user permissions} required for users to use this command.
     *
     * @return The required user permissions.
     */
    @Override
    public @NotNull ConcurrentSet<UserPermission> getUserPermissions() {
        return Concurrent.newUnmodifiableSet();
    }

    public final boolean isEnabled() {
        return this.getConfig().isEnabled();
    }

    protected abstract @NotNull Mono<Void> process(@NotNull CommandContext<?> commandContext) throws DiscordException;

    @Override
    public final @NotNull Mono<Void> apply(@NotNull CommandContext<?> commandContext) {
        return commandContext.withEvent(event -> commandContext.withGuild(optionalGuild -> commandContext.withChannel(messageChannel -> commandContext
            .deferReply()
            .then(Mono.fromCallable(() -> {
                CommandConfigModel commandConfigModel = this.getConfig();

                // Handle Disabled Command
                if (!this.isEnabled())
                    throw SimplifiedException.of(DisabledCommandException.class).withMessage("This command is currently disabled!").build();

                // Handle Disabled Guild
                commandContext.getGuildId()
                    .flatMap(this::getGuild)
                    .ifPresent(guild -> {
                        if (!guild.isDeveloperBotEnabled())
                            throw SimplifiedException.of(DisabledCommandException.class).withMessage("The bot was disabled in this guild by the developer!").build();
                    });

                // Handle Developer Command
                if (commandConfigModel.isDeveloperOnly() && !this.doesUserHavePermissions(commandContext.getInteractUserId(), optionalGuild, UserPermission.DEVELOPER))
                    throw SimplifiedException.of(UserPermissionException.class)
                        .withMessage("Only the bot developer can run this command!")
                        .build();

                // Handle Bot Permissions
                if (!commandContext.isPrivateChannel()) {
                    // Handle Inherited Permissions
                    if (commandConfigModel.isInheritingPermissions()) {
                        this.getParentCommands().forEach(parentRelationship -> {
                            if (!this.hasChannelPermissions(this.getDiscordBot().getClientId(), commandContext.getChannel().ofType(GuildChannel.class), parentRelationship.getPermissions()))
                                throw SimplifiedException.of(BotPermissionException.class)
                                    .withMessage("The parent command ''{0}'' lacks permissions required to run!", parentRelationship.getName())
                                    .addData("ID", this.getDiscordBot().getClientId())
                                    .addData("PERMISSIONS", parentRelationship.getPermissions())
                                    .build();
                        });
                    }

                    // Handle Command Permissions
                    if (!this.hasChannelPermissions(this.getDiscordBot().getClientId(), commandContext.getChannel().ofType(GuildChannel.class), this.getPermissions()))
                        throw SimplifiedException.of(BotPermissionException.class)
                            .withMessage("The command ''{0}'' lacks permissions required to run!", this.getConfig().getName())
                            .addData("ID", this.getDiscordBot().getClientId())
                            .addData("PERMISSIONS", this.getPermissions())
                            .build();
                }

                // Handle User Permission
                if (!this.doesUserHavePermissions(commandContext.getInteractUserId(), optionalGuild, this.getUserPermissions()))
                    throw SimplifiedException.of(UserPermissionException.class)
                        .withMessage("You are missing permissions required to run this command!")
                        .build();

                // Validate Arguments
                for (Argument argument : commandContext.getArguments()) {
                    if (argument.getParameter().isRemainder())
                        break;

                    if (argument.getValue().isEmpty()) {
                        if (argument.getParameter().isRequired())
                            throw SimplifiedException.of(MissingParameterException.class)
                                .addData("ARGUMENT", argument)
                                .addData("MISSING", true)
                                .build();
                    } else {
                        if (!argument.getParameter().getType().isValid(argument.getValue()))
                            throw SimplifiedException.of(InvalidParameterException.class)
                                .addData("ARGUMENT", argument)
                                .addData("MISSING", false)
                                .build();

                        if (!argument.getParameter().isValid(argument.getValue(), commandContext))
                            throw SimplifiedException.of(InvalidParameterException.class)
                                .addData("ARGUMENT", argument)
                                .addData("MISSING", false)
                                .build();
                    }
                }

                // Process Command
                return this.process(commandContext);
            }))
            .flatMap(Function.identity())
            .onErrorResume(throwable -> this.getDiscordBot().handleException(ExceptionContext.of(commandContext, throwable)))
        )));
    }

}
