package dev.sbs.discordapi.command;

import dev.sbs.api.SimplifiedException;
import dev.sbs.api.client.exception.HypixelApiException;
import dev.sbs.api.client.exception.MojangApiException;
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
import dev.sbs.discordapi.command.exception.InvalidParameterException;
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
import discord4j.core.object.reaction.ReactionEmoji;
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

    public final Optional<ReactionEmoji> getEmoji() {
        return this.getCommandEmoji(this.getClass());
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
        return this.getCommandInfo().enabled();
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
                    throw SimplifiedException.of(DisabledCommandException.class).build();

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

                // Handle Help Arguments
                if (commandContext.getArguments().size() > 0 && helpArguments.contains(commandContext.getArguments().get(commandContext.getArguments().size() - 1).getValue().orElse("")))
                    throw SimplifiedException.of(HelpCommandException.class).build();

                // Validate Arguments
                for (Argument argument : commandContext.getArguments()) {
                    if (argument.getParameter().isRemainder())
                        break;

                    if (!argument.getParameter().getType().isValid(argument.getValue().orElse("")))
                        throw SimplifiedException.of(InvalidParameterException.class)
                            .addData("ARGUMENT", argument)
                            .build();

                    if (!argument.getParameter().isValid(argument.getValue().orElse(""), commandContext))
                        throw SimplifiedException.of(InvalidParameterException.class)
                            .addData("ARGUMENT", argument)
                            .build();
                }

                // Process Command
                this.process(commandContext);
            } catch (DisabledCommandException disabledCommandException) {
                userErrorBuilder = Optional.of(
                    Embed.builder()
                        .withAuthor("Disabled Command", Emoji.of(929250287431057468L, "status_disabled").getUrl())
                        .withDescription("This command is currently disabled.")
                );
            } catch (PermissionException permissionException) {
                boolean botPermissions = (permissionException instanceof BotPermissionException);

                Embed.EmbedBuilder embedBuilder = Embed.builder()
                    .withAuthor(FormatUtil.format("Missing {0} Permissions", (botPermissions ? "Bot" : "User")), Emoji.of(929250287443656734L, "status_high_importance").getUrl())
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
                                    .map(value -> "no") // TODO: Emoji
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
            } catch (InvalidParameterException invalidParameterException) {
                Argument argument = (Argument) invalidParameterException.getData().get("ARGUMENT");
                Parameter parameter = argument.getParameter();

                userErrorBuilder = Optional.of(
                    Embed.builder()
                        .withAuthor("Invalid Parameter", Emoji.of(929250313821638666L, "status_info").getUrl())
                        .withDescription("Your provided argument does not validate against expected parameter.")
                        .withFields(
                            Field.of(
                                "Parameter",
                                parameter.getName(),
                                true
                            ),
                            Field.of(
                                "Description",
                                parameter.getDescription(),
                                true
                            ),
                            Field.of(
                                "Type",
                                parameter.getType().name(),
                                true
                            )
                        )
                        .withField(
                            "Argument",
                            argument.getValue().orElse("")
                        )
                );
            } catch (MojangApiException mojangApiException) {
                userErrorBuilder = Optional.of(
                    Embed.builder()
                        .withAuthor("Mojang Api Error", Emoji.of(929250169973780480L, "cloud_disabled").getUrl())
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
                        .withAuthor("Hypixel Api Error", Emoji.of(929250169973780480L, "cloud_disabled").getUrl())
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
            .withAuthor("Help", Emoji.of(929250313821638666L, "status_info").getUrl())
            .withTitle("Command :: {0}", commandInfo.name())
            .withDescription(commandInfo.longDescription())
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

        if (ListUtil.notEmpty(commandInfo.aliases())) {
            embedBuilder.withField(
                "Aliases",
                StringUtil.join(
                    commandInfo.aliases(),
                    ", "
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
