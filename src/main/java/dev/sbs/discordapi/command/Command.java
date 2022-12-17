package dev.sbs.discordapi.command;

import dev.sbs.api.data.model.discord.command_categories.CommandCategoryModel;
import dev.sbs.api.data.model.discord.command_configs.CommandConfigModel;
import dev.sbs.api.data.model.discord.command_groups.CommandGroupModel;
import dev.sbs.api.data.model.discord.guild_data.guild_command_configs.GuildCommandConfigModel;
import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.unmodifiable.ConcurrentUnmodifiableList;
import dev.sbs.api.util.data.mutable.MutableBoolean;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.data.Argument;
import dev.sbs.discordapi.command.data.CommandData;
import dev.sbs.discordapi.command.data.CommandInfo;
import dev.sbs.discordapi.command.data.Parameter;
import dev.sbs.discordapi.command.exception.CommandException;
import dev.sbs.discordapi.command.exception.DisabledCommandException;
import dev.sbs.discordapi.command.exception.parameter.InvalidParameterException;
import dev.sbs.discordapi.command.exception.parameter.MissingParameterException;
import dev.sbs.discordapi.command.exception.permission.BotPermissionException;
import dev.sbs.discordapi.command.exception.permission.UserPermissionException;
import dev.sbs.discordapi.context.CommandContext;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.util.base.DiscordHelper;
import dev.sbs.discordapi.util.exception.DiscordException;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.GuildChannel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.awt.*;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

public abstract class Command extends DiscordHelper implements CommandData, Function<CommandContext<?>, Mono<Void>> {

    private static final ConcurrentUnmodifiableList<Parameter> NO_PARAMETERS = Concurrent.newUnmodifiableList();
    private static final ConcurrentUnmodifiableList<String> NO_EXAMPLES = Concurrent.newUnmodifiableList();
    private final static Pattern validCommandPattern = Pattern.compile("^[\\w-]{1,32}$");
    private final static ConcurrentList<String> helpArguments = Concurrent.newUnmodifiableList("help", "?");
    @Getter private final @NotNull DiscordBot discordBot;
    @Getter private final CommandInfo commandInfo;

    protected Command(@NotNull DiscordBot discordBot) {
        super(discordBot);
        this.discordBot = discordBot;

        // Validate Command Annotation
        this.commandInfo = this.getCommandAnnotation(this.getClass())
            .orElseThrow(() -> SimplifiedException.of(CommandException.class)
                .withMessage("''{0}'' command must be annotated by the @Command annotation!", this.getClass().getName())
                .build()
            );

        // Validate Command Name
        if (!validCommandPattern.matcher(this.getCommandInfo().name()).matches())
            throw SimplifiedException.of(CommandException.class)
                .withMessage("''{0}'' command name ''{1}'' must only contain english characters!", this.getClass().getName(), this.getCommandInfo().name())
                .build();
    }

    public final @NotNull Optional<CommandCategoryModel> getCategory() {
        return Optional.ofNullable(this.getCommandConfig().getCategory());
    }

    public final @NotNull CommandConfigModel getCommandConfig() {
        return this.getCommandConfig(this);
    }

    public final @NotNull String getCommandPath(boolean slashCommand) {
        // Get Root Command Prefix
        String rootCommand = this.getDiscordBot()
            .getRootCommandRelationship()
            .getOptionalCommandInfo()
            .map(CommandInfo::name)
            .orElse("");

        return FormatUtil.format("{0}{1}", (slashCommand ? "/" : rootCommand + " "), super.getCommandPath(this));
    }

    public final @NotNull String getDescription() {
        return this.getCommandConfig().getDescription();
    }

    public final @NotNull Optional<CommandGroupModel> getGroup() {
        return Optional.ofNullable(this.getCommandConfig().getGroup());
    }

    public final @NotNull Optional<String> getLongDescription() {
        return Optional.ofNullable(this.getCommandConfig().getLongDescription());
    }

    public final @NotNull Optional<GuildCommandConfigModel> getGuildCommandConfig() {
        return this.getGuildCommandConfig(this.getCommandInfo());
    }

    public final @NotNull ConcurrentList<RelationshipData> getParentCommandList() {
        return this.getParentCommandList(this.getClass());
    }

    public final @NotNull ConcurrentList<String> getParentCommandNames() {
        return this.getParentCommandList()
            .stream()
            .map(RelationshipData::getOptionalCommandInfo)
            .flatMap(Optional::stream)
            .map(CommandInfo::name)
            .collect(Concurrent.toList());
    }

    public final @NotNull Optional<Emoji> getEmoji() {
        return Emoji.of(this.getCommandConfig().getEmoji());
    }

    public @NotNull ConcurrentUnmodifiableList<String> getExampleArguments() {
        return NO_EXAMPLES;
    }

    public final Optional<Parameter> getParameter(int index) {
        ConcurrentList<Parameter> parameters = this.getParameters();
        index = Math.max(0, index);
        return Optional.ofNullable(index < parameters.size() ? parameters.get(index) : null);
    }

    public @NotNull ConcurrentUnmodifiableList<Parameter> getParameters() {
        return NO_PARAMETERS;
    }

    public final @NotNull ConcurrentList<UserPermission> getUserPermissions() {
        return Concurrent.newUnmodifiableList(this.getCommandInfo().userPermissions());
    }

    public final boolean isEnabled() {
        return this.getCommandConfig().isEnabled();
    }

    protected abstract @NotNull Mono<Void> process(@NotNull CommandContext<?> commandContext) throws DiscordException;

    @Override
    public final @NotNull Mono<Void> apply(@NotNull CommandContext<?> commandContext) {
        return commandContext.withEvent(event -> commandContext.withGuild(optionalGuild -> commandContext.withChannel(messageChannel -> commandContext
            .deferReply()
            .then(Mono.fromCallable(() -> {
                CommandConfigModel commandConfigModel = this.getCommandConfig();

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
                        this.getParentCommandList()
                            .stream()
                            .map(RelationshipData::getOptionalCommandInfo)
                            .flatMap(Optional::stream)
                            .forEach(annoParentCommand -> {
                                if (!this.hasChannelPermissions(this.getDiscordBot().getClientId(), commandContext.getChannel().ofType(GuildChannel.class), annoParentCommand.permissions()))
                                    throw SimplifiedException.of(BotPermissionException.class)
                                        .withMessage("The parent command ''{0}'' lacks permissions required to run!", annoParentCommand.name())
                                        .addData("ID", this.getDiscordBot().getClientId())
                                        .addData("PERMISSIONS", annoParentCommand.permissions())
                                        .build();
                            });
                    }

                    // Handle Command Permissions
                    if (!this.hasChannelPermissions(this.getDiscordBot().getClientId(), commandContext.getChannel().ofType(GuildChannel.class), this.getCommandInfo().permissions()))
                        throw SimplifiedException.of(BotPermissionException.class)
                            .withMessage("The command ''{0}'' lacks permissions required to run!", this.getCommandInfo().name())
                            .addData("ID", this.getDiscordBot().getClientId())
                            .addData("PERMISSIONS", this.getCommandInfo().permissions())
                            .build();
                }

                // Handle User Permission
                if (!this.doesUserHavePermissions(commandContext.getInteractUserId(), optionalGuild, this.getUserPermissions())) {
                    MutableBoolean hasPermissions = new MutableBoolean(false);

                    // Handle Guild Override Permissions
                    if (commandConfigModel.isGuildPermissible()) {
                        optionalGuild.ifPresent(guild -> this.getGuildCommandConfig().ifPresent(guildCommandConfigModel -> {
                            // User Override
                            if (guildCommandConfigModel.getUsers().contains(commandContext.getInteractUserId().asLong()))
                                hasPermissions.setTrue();
                            else {
                                // Role Override
                                boolean hasRole = guild.getMemberById(commandContext.getInteractUserId())
                                    .map(member -> member.getRoleIds()
                                        .stream()
                                        .map(Snowflake::asLong)
                                        .anyMatch(roleId -> guildCommandConfigModel.getRoles().contains(roleId))
                                    )
                                    .blockOptional()
                                    .orElse(false);

                                if (hasRole)
                                    hasPermissions.setTrue();
                                else if (StringUtil.isNotEmpty(guildCommandConfigModel.getPermissionOverride())) {
                                    // Permission Override
                                    hasPermissions.set(this.doesUserHavePermissions(
                                        commandContext.getInteractUserId(),
                                        optionalGuild,
                                        UserPermission.valueOf(guildCommandConfigModel.getPermissionOverride())
                                    ));
                                }
                            }
                        }));
                    }

                    if (!hasPermissions.get())
                        throw SimplifiedException.of(UserPermissionException.class)
                            .withMessage("You are missing permissions required to run this command!")
                            .build();
                }

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

    public interface RelationshipData {

        Optional<CommandInfo> getOptionalCommandInfo();
        Class<? extends CommandData> getCommandClass();
        ConcurrentList<Relationship> getSubCommands();

    }

    @RequiredArgsConstructor
    public static class Relationship implements RelationshipData {

        @Getter private final CommandInfo commandInfo;
        @Getter private final Class<? extends Command> commandClass;
        @Getter private final Command instance;
        @Getter private final ConcurrentList<Relationship> subCommands;

        public Embed createHelpEmbed() {
            return this.createHelpEmbed(true);
        }

        public Embed createHelpEmbed(boolean isSlashCommand) {
            String commandPath = this.getInstance().getCommandPath(isSlashCommand);
            CommandInfo commandInfo = this.getCommandInfo();
            ConcurrentList<Parameter> parameters = this.getInstance().getParameters();

            Embed.EmbedBuilder embedBuilder = Embed.builder()
                .withAuthor("Help", getEmoji("STATUS_INFO").map(Emoji::getUrl))
                .withTitle("Command :: {0}", commandInfo.name())
                .withDescription(this.getInstance().getCommandConfig().getLongDescription())
                .withTimestamp(Instant.now())
                .withColor(Color.DARK_GRAY);

            if (ListUtil.notEmpty(parameters)) {
                embedBuilder.withField(
                    "Usage",
                    FormatUtil.format(
                        """
                            <> - Required Parameters
                            [] - Optional Parameters
    
                            {0} {1}""",
                        commandPath,
                        StringUtil.join(
                            parameters.stream()
                                .map(parameter -> parameter.isRequired() ? FormatUtil.format("<{0}>", parameter.getName()) : FormatUtil.format("[{0}]", parameter.getName()))
                                .collect(Concurrent.toList()),
                            " "
                        )
                    )
                );
            }

            if (ListUtil.notEmpty(this.getInstance().getExampleArguments())) {
                embedBuilder.withField(
                    "Examples",
                    StringUtil.join(
                        this.getInstance()
                            .getExampleArguments()
                            .stream()
                            .map(example -> FormatUtil.format("{0} {1}", commandPath, example))
                            .collect(Concurrent.toList()),
                        "\n"
                    )
                );
            }

            return embedBuilder.build();
        }

        @Override
        public Optional<CommandInfo> getOptionalCommandInfo() {
            return Optional.of(this.getCommandInfo());
        }

    }

    @RequiredArgsConstructor
    public static class RootRelationship implements RelationshipData {

        public static final RootRelationship DEFAULT = new RootRelationship(
            Optional.empty(),
            PrefixCommand.class,
            Concurrent.newUnmodifiableList()
        );

        @Getter private final Optional<CommandInfo> optionalCommandInfo;
        @Getter private final Class<? extends PrefixCommand> commandClass;
        @Getter private final ConcurrentList<Relationship> subCommands;

    }

}
