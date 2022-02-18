package dev.sbs.discordapi.command;

import dev.sbs.api.SimplifiedException;
import dev.sbs.api.client.exception.HypixelApiException;
import dev.sbs.api.client.exception.MojangApiException;
import dev.sbs.api.data.model.discord.command_configs.CommandConfigModel;
import dev.sbs.api.data.model.discord.guild_command_configs.GuildCommandConfigModel;
import dev.sbs.api.util.concurrent.Concurrent;
import dev.sbs.api.util.concurrent.ConcurrentList;
import dev.sbs.api.util.concurrent.linked.ConcurrentLinkedMap;
import dev.sbs.api.util.concurrent.unmodifiable.ConcurrentUnmodifiableList;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.data.Argument;
import dev.sbs.discordapi.command.data.CommandData;
import dev.sbs.discordapi.command.data.CommandGroup;
import dev.sbs.discordapi.command.data.CommandInfo;
import dev.sbs.discordapi.command.data.Parameter;
import dev.sbs.discordapi.command.exception.CommandException;
import dev.sbs.discordapi.command.exception.DisabledCommandException;
import dev.sbs.discordapi.command.exception.HelpCommandException;
import dev.sbs.discordapi.command.exception.parameter.InvalidParameterException;
import dev.sbs.discordapi.command.exception.parameter.MissingParameterException;
import dev.sbs.discordapi.command.exception.parameter.ParameterException;
import dev.sbs.discordapi.command.exception.permission.BotPermissionException;
import dev.sbs.discordapi.command.exception.permission.PermissionException;
import dev.sbs.discordapi.command.exception.permission.UserPermissionException;
import dev.sbs.discordapi.context.command.CommandContext;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.util.DiscordObject;
import dev.sbs.discordapi.util.exception.DiscordException;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.rest.util.Permission;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.awt.*;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

public abstract class Command extends DiscordObject implements CommandData, Function<CommandContext<?>, Mono<Void>> {

    private static final ConcurrentUnmodifiableList<Parameter> NO_PARAMETERS = Concurrent.newUnmodifiableList();
    private static final ConcurrentUnmodifiableList<String> NO_EXAMPLES = Concurrent.newUnmodifiableList();
    private static final ConcurrentUnmodifiableList<CommandGroup> NO_COMMAND_GROUPS = Concurrent.newUnmodifiableList();
    private final static Pattern validCommandPattern = Pattern.compile("^[\\w-]{1,32}$");
    private final static ConcurrentList<String> helpArguments = Concurrent.newUnmodifiableList("help", "?");
    @Getter private final CommandInfo commandInfo;

    protected Command(DiscordBot discordBot) {
        super(discordBot);

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

    public final @NotNull Optional<CommandConfigModel> getCommandConfig() {
        return this.getCommandConfig(this.getCommandInfo());
    }

    public final @NotNull Optional<String> getDescription() {
        return Optional.ofNullable(
            this.getCommandConfig()
                .map(CommandConfigModel::getDescription)
                .orElse(StringUtil.defaultIfEmpty(this.getCommandInfo().description(), null))
        );
    }

    public final @NotNull Optional<String> getLongDescription() {
        return Optional.ofNullable(
            this.getCommandConfig()
                .map(CommandConfigModel::getLongDescription)
                .orElse(StringUtil.defaultIfEmpty(this.getCommandInfo().longDescription(), null))
        );
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

    public final Optional<Emoji> getEmoji() {
        return this.getCommandConfig().map(CommandConfigModel::getEmoji).map(Emoji::of);
    }

    public @NotNull ConcurrentUnmodifiableList<String> getExamples() {
        return NO_EXAMPLES;
    }

    public @NotNull ConcurrentUnmodifiableList<CommandGroup> getGroups() {
        return NO_COMMAND_GROUPS;
    }

    public final Optional<Parameter> getParameter(int index) {
        ConcurrentList<Parameter> parameters = this.getParameters();
        return Optional.ofNullable(index < parameters.size() ? parameters.get(index) : null);
    }

    public @NotNull ConcurrentUnmodifiableList<Parameter> getParameters() {
        return NO_PARAMETERS;
    }

    public final @NotNull ConcurrentList<UserPermission> getUserPermissions() {
        return Concurrent.newUnmodifiableList(this.getCommandInfo().userPermissions());
    }

    public final boolean isEnabled() {
        return this.getCommandConfig().map(CommandConfigModel::isEnabled).orElse(true);
    }

    protected abstract void process(CommandContext<?> commandContext) throws DiscordException;

    @Override
    public Mono<Void> apply(CommandContext<?> commandContext) {
        return commandContext.withEvent(event -> commandContext.withChannel(messageChannel -> {
            Optional<Embed.EmbedBuilder> userErrorBuilder = Optional.empty();
            String parentCommands = StringUtil.join(this.getParentCommandNames(), " ");

            try {
                // Handle Disabled Command
                if (!this.isEnabled())
                    throw SimplifiedException.of(DisabledCommandException.class).withMessage("This command is currently disabled!").build();

                // Handle Guild Permissions
                if (!commandContext.isPrivateChannel()) {
                    // Handle Inherited Permissions
                    if (this.getCommandInfo().inherit()) {
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
                if (!this.doesUserHavePermissions(commandContext.getInteractUserId(), commandContext.getGuild(), this.getUserPermissions()))
                    throw SimplifiedException.of(UserPermissionException.class)
                        .withMessage("You are missing permissions required to run this command!")
                        .build();

                // Handle Database Overrides
                this.getCommandConfig().ifPresent(commandConfig -> {
                    // Handle Developer Overrides
                    if (commandConfig.isDeveloperOnly() && !this.doesUserHavePermissions(commandContext.getInteractUserId(), commandContext.getGuild(), UserPermission.BOT_OWNER))
                        throw SimplifiedException.of(UserPermissionException.class)
                            .withMessage("You are missing permissions required to run this command!")
                            .build();

                    commandContext.getGuildId()
                        .flatMap(this::getGuild)
                        .ifPresent(guild -> {
                            if (!guild.isDeveloperBotEnabled())
                                throw SimplifiedException.of(DisabledCommandException.class).withMessage("The bot was disabled in this guild by the developer!").build();
                        });

                    // Handle Guild Overrides
                    if (commandConfig.isGuildToggleable()) {
                        this.getGuildCommandConfig().ifPresent(guildCommandConfig -> {
                            if (!guildCommandConfig.isEnabled())
                                throw SimplifiedException.of(DisabledCommandException.class).withMessage("This command is disabled in this guild!").build();
                        });
                    }
                });

                // Handle Help Arguments
                if (commandContext.getArguments().size() > 0 && helpArguments.contains(commandContext.getArguments().get(commandContext.getArguments().size() - 1).getValue().orElse("")))
                    throw SimplifiedException.of(HelpCommandException.class).build();

                // Validate Arguments
                for (Argument argument : commandContext.getArguments()) {
                    if (argument.getParameter().isRemainder())
                        break;

                    if (argument.getValue().isEmpty()) {
                        if (argument.getParameter().isRequired())
                            throw SimplifiedException.of(MissingParameterException.class)
                                .addData("MISSING", true)
                                .build();
                    } else {
                        if (!argument.getParameter().getType().isValid(argument.getValue().orElse("")))
                            throw SimplifiedException.of(InvalidParameterException.class)
                                .addData("ARGUMENT", argument)
                                .addData("MISSING", false)
                                .build();

                        if (!argument.getParameter().isValid(argument.getValue().orElse(""), commandContext))
                            throw SimplifiedException.of(InvalidParameterException.class)
                                .addData("ARGUMENT", argument)
                                .addData("MISSING", false)
                                .build();
                    }
                }

                // Process Command
                this.process(commandContext);
            } catch (DisabledCommandException disabledCommandException) {
                userErrorBuilder = Optional.of(
                    Embed.builder()
                        .withAuthor("Disabled Command", getEmoji("STATUS_DISABLED").map(Emoji::getUrl))
                        .withDescription("This command is currently disabled.")
                );
            } catch (PermissionException permissionException) {
                boolean botPermissions = (permissionException instanceof BotPermissionException);

                Embed.EmbedBuilder embedBuilder = Embed.builder()
                    .withAuthor(FormatUtil.format("Missing {0} Permissions", (botPermissions ? "Bot" : "User")), getEmoji("STATUS_HIGH_IMPORTANCE").map(Emoji::getUrl))
                    .withDescription(permissionException.getMessage());

                if (botPermissions) {
                    Snowflake snowflake = (Snowflake) permissionException.getData().get("ID");
                    Permission[] permissions = (Permission[]) permissionException.getData().get("PERMISSIONS");
                    ConcurrentLinkedMap<Permission, Boolean> permissionMap = this.getChannelPermissionMap(snowflake, commandContext.getChannel().ofType(GuildChannel.class), permissions);

                    embedBuilder.withField(
                            "Required Permissions",
                            StringUtil.join(
                                permissionMap.stream()
                                    .filter(entry -> !entry.getValue())
                                    .map(Map.Entry::getKey)
                                    .map(Permission::name)
                                    .collect(Concurrent.toList()),
                                "\n"
                            ),
                            true
                        )
                        .withField(
                            "Status",
                            StringUtil.join(
                                permissionMap.stream()
                                    .map(Map.Entry::getValue)
                                    .filter(value -> !value)
                                    .map(value -> getEmoji("ACTION_DENY").map(Emoji::asFormat).orElse("No"))
                                    .collect(Concurrent.toList()),
                                "\n"
                            ),
                            true
                        )
                        .withEmptyField(true);
                }

                userErrorBuilder = Optional.of(embedBuilder);
            } catch (HelpCommandException helpCommandException) {
                userErrorBuilder = Optional.of(createHelpEmbedBuilder(commandContext.getRelationship()));
            } catch (ParameterException parameterException) {
                Argument argument = (Argument) parameterException.getData().get("ARGUMENT");
                Parameter parameter = argument.getParameter();
                boolean missing = (boolean) parameterException.getData().get("MISSING");
                String missingDescription = "You did not provide a required parameter.";
                String invalidDescription = "The provided argument does not validate against the expected parameter.";

                Embed.EmbedBuilder embedBuilder = Embed.builder()
                    .withAuthor(
                        FormatUtil.format("{0} Parameter", (missing ? "Missing" : "Invalid")),
                        getEmoji("STATUS_INFO").map(Emoji::getUrl)
                    )
                    .withDescription(missing ? missingDescription : invalidDescription)
                    .withFields(
                        Field.of(
                            "Parameter",
                            parameter.getName(),
                            true
                        ),
                        Field.of(
                            "Required",
                            parameter.isRequired() ? "Yes" : "No",
                            true
                        ),
                        Field.of(
                            "Type",
                            parameter.getType().name(),
                            true
                        )
                    )
                    .withField(
                        "Description",
                        parameter.getDescription()
                    );

                if (!missing)
                    embedBuilder.withField(
                        "Argument",
                        argument.getValue().orElse(getEmoji("TEXT_NULL").map(Emoji::asFormat).orElse("*<null>*"))
                    );

                userErrorBuilder = Optional.of(embedBuilder);
            } catch (MojangApiException mojangApiException) {
                userErrorBuilder = Optional.of(
                    Embed.builder()
                        .withAuthor("Mojang Api Error", getEmoji("CLOUD_DISABLED").map(Emoji::getUrl))
                        .withDescription(mojangApiException.getMessage())
                        .withFields(
                            Field.of(
                                "State",
                                mojangApiException.getHttpStatus().getState().getTitle(),
                                true
                            ),
                            Field.of(
                                "Code",
                                String.valueOf(mojangApiException.getHttpStatus().getCode()),
                                true
                            ),
                            Field.of(
                                "Message",
                                mojangApiException.getHttpStatus().getMessage(),
                                true
                            )
                        )
                        .withField(
                            "Reason",
                            mojangApiException.getErrorResponse().getReason()
                        )
                );
            } catch (HypixelApiException hypixelApiException) {
                userErrorBuilder = Optional.of(
                    Embed.builder()
                        .withAuthor("Hypixel Api Error", getEmoji("CLOUD_DISABLED").map(Emoji::getUrl))
                        .withDescription(hypixelApiException.getMessage())
                        .withFields(
                            Field.of(
                                "State",
                                hypixelApiException.getHttpStatus().getState().getTitle(),
                                true
                            ),
                            Field.of(
                                "Code",
                                String.valueOf(hypixelApiException.getHttpStatus().getCode()),
                                true
                            ),
                            Field.of(
                                "Message",
                                hypixelApiException.getHttpStatus().getMessage(),
                                true
                            )
                        )
                        .withField(
                            "Reason",
                            hypixelApiException.getErrorResponse().getReason()
                        )
                );
            } catch (Exception uncaughtException) {
                this.getDiscordBot().handleUncaughtException(
                    ExceptionContext.of(
                        this.getDiscordBot(),
                        commandContext,
                        uncaughtException,
                        "Command Exception",
                        embedBuilder -> embedBuilder.withFields(
                            Field.of(
                                "Parent Commands",
                                StringUtil.join(this.getParentCommandNames(), " "),
                                true
                            ),
                            Field.of(
                                "Command",
                                this.getCommandInfo().name(),
                                true
                            ),
                            Field.of(
                                "Arguments",
                                StringUtil.join(commandContext.getArguments(), " "),
                                true
                            )
                        )
                    )
                );
            }

            // Handle User Error Response
            return userErrorBuilder.map(embedBuilder -> commandContext.softReply(
                    Response.builder()
                        .isInteractable(false)
                        .withEmbeds(
                            embedBuilder.withColor(Color.DARK_GRAY)
                                .withTitle("Command :: {0} {1}", parentCommands, this.getCommandInfo().name())
                                .withTimestamp(Instant.now())
                                .build()
                        )
                        .withReference(commandContext)
                        .build()
                ))
                .orElse(Mono.empty());
        }));
    }

    private static Embed.EmbedBuilder createHelpEmbedBuilder(Relationship relationship) {
        String parentCommands = StringUtil.join(relationship.getInstance().getParentCommandNames(), " ");
        CommandInfo commandInfo = relationship.getCommandInfo();
        ConcurrentList<Parameter> parameters = relationship.getInstance().getParameters();

        Embed.EmbedBuilder embedBuilder = Embed.builder()
            .withAuthor("Help", getEmoji("STATUS_INFO").map(Emoji::getUrl))
            .withTitle("Command :: {0}", commandInfo.name())
            .withDescription(relationship.getInstance().getLongDescription())
            .withTimestamp(Instant.now())
            .withColor(Color.DARK_GRAY);

        if (ListUtil.notEmpty(parameters)) {
            embedBuilder.withField(
                "Usage",
                FormatUtil.format(
                    """
                        <> - Required Parameters
                        [] - Optional Parameters

                        {0} {1} {2}""",
                    parentCommands,
                    commandInfo.name(),
                    StringUtil.join(
                        parameters.stream()
                            .map(parameter -> parameter.isRequired() ? FormatUtil.format("<{0}>", parameter.getName()) : FormatUtil.format("[{0}]", parameter.getName()))
                            .collect(Concurrent.toList()),
                        " "
                    )
                )
            );
        }

        if (ListUtil.notEmpty(relationship.getInstance().getExamples())) {
            embedBuilder.withField(
                "Examples",
                StringUtil.join(
                    relationship.getInstance()
                        .getExamples()
                        .stream()
                        .map(example -> FormatUtil.format("{0} {1} {2}", parentCommands, commandInfo.name(), example))
                        .collect(Concurrent.toList()),
                    "\n"
                )
            );
        }

        return embedBuilder;
    }

    public static Embed createHelpEmbed(Relationship relationship) {
        return createHelpEmbedBuilder(relationship).build();
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

        @Override
        public Optional<CommandInfo> getOptionalCommandInfo() {
            return Optional.of(this.getCommandInfo());
        }

    }

    @RequiredArgsConstructor
    public static class RootRelationship implements RelationshipData {

        public static final RootRelationship DEFAULT = new RootRelationship(Optional.empty(), PrefixCommand.class, Concurrent.newUnmodifiableList());
        @Getter private final Optional<CommandInfo> optionalCommandInfo;
        @Getter private final Class<? extends PrefixCommand> commandClass;
        @Getter private final ConcurrentList<Relationship> subCommands;

    }

}
