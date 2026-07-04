package dev.simplified.discordapi.handler;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.collection.ConcurrentSet;
import dev.simplified.collection.StreamUtil;
import dev.simplified.collection.tuple.pair.Pair;
import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.CommandStateResolver;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.Structure;
import dev.simplified.discordapi.command.parameter.Parameter;
import dev.simplified.discordapi.context.command.MessageCommandContext;
import dev.simplified.discordapi.context.command.SlashCommandContext;
import dev.simplified.discordapi.context.command.UserCommandContext;
import dev.simplified.discordapi.context.scope.CommandContext;
import dev.simplified.discordapi.util.DiscordReference;
import dev.simplified.reflection.Reflection;
import dev.simplified.util.StringUtil;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.object.command.ApplicationCommand;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.channel.Channel;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.rest.util.PermissionSet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Central registry for {@link DiscordCommand} instances, responsible for
 * classpath discovery, validation, Discord API registration, and
 * mapping of application command IDs back to their command classes.
 *
 * <p>
 * On construction the handler validates all discovered command classes
 * (checking for the required {@link Structure} annotation and a valid
 * command name), filters them by {@link DiscordCommand.Type type}
 * (slash, user, message), and detects duplicate registrations.
 * The resulting lists are available via {@link #getSlashCommands()},
 * {@link #getUserCommands()}, and {@link #getMessageCommands()}.
 *
 * <p>
 * Use {@link #updateApplicationCommands()} to bulk-overwrite global
 * and guild application commands with the Discord API.
 *
 * @see DiscordCommand
 * @see Structure
 */
@Log4j2
@SuppressWarnings("rawtypes")
public final class CommandHandler extends DiscordReference {

    /** Regex enforcing Discord's 1-32 character alphanumeric command name constraint. */
    private static final Pattern validCommandPattern = Pattern.compile("^[\\w-]{1,32}$");

    /** Mapping from command class to its Discord-assigned application command ID. */
    private final @NotNull ConcurrentMap<Class<? extends DiscordCommand>, Long> commandIds = Concurrent.newMap();

    /** Source of localization overrides injected into every command spec. */
    private final @NotNull LocaleHandler localeHandler;

    /** Resolver deciding whether a command is enabled at dispatch time. */
    @Getter private final @NotNull CommandStateResolver stateResolver;

    /** All validated and de-duplicated command instances. */
    @Getter private final @NotNull ConcurrentList<DiscordCommand> loadedCommands;

    /** Filtered list of slash (chat input) commands. */
    @Getter private final @NotNull ConcurrentList<DiscordCommand<SlashCommandContext>> slashCommands;

    /** Filtered list of user context-menu commands. */
    @Getter private final @NotNull ConcurrentList<DiscordCommand<UserCommandContext>> userCommands;

    /** Filtered list of message context-menu commands. */
    @Getter private final @NotNull ConcurrentList<DiscordCommand<MessageCommandContext>> messageCommands;

    /**
     * Constructs a new {@code CommandHandler} by validating, instantiating,
     * and filtering the given command classes into typed lists.
     *
     * @param discordBot the bot this handler belongs to
     * @param commands the set of command classes discovered via classpath scanning
     * @param localeHandler the locale handler consulted while building command specs
     */
    CommandHandler(
        @NotNull DiscordBot discordBot,
        @NotNull ConcurrentSet<Class<DiscordCommand>> commands,
        @NotNull LocaleHandler localeHandler
    ) {
        super(discordBot);
        this.localeHandler = localeHandler;
        this.stateResolver = discordBot.getConfig().getCommandStateResolver();

        this.getLog().info("Validating Commands");
        this.loadedCommands = this.validateCommands(discordBot, commands);
        this.localeHandler.applyProgrammaticOverrides(this.loadedCommands);

        this.getLog().info("Filtering Commands");
        this.slashCommands = this.retrieveTypedCommands(
            DiscordCommand.Type.CHAT_INPUT,
            this.loadedCommands,
            (commandEntry, compareEntry) -> Objects.equals(
                commandEntry.getStructure().parent(),
                compareEntry.getStructure().parent()
            ) && Objects.equals(
                commandEntry.getStructure().group(),
                compareEntry.getStructure().group()
            ) && commandEntry.getStructure().name().equalsIgnoreCase(compareEntry.getStructure().name())
        );

        this.userCommands = this.retrieveTypedCommands(
            DiscordCommand.Type.USER,
            this.loadedCommands,
            (commandEntry, compareEntry) -> commandEntry.getStructure().name().equalsIgnoreCase(compareEntry.getStructure().name())
        );

        this.messageCommands = this.retrieveTypedCommands(
            DiscordCommand.Type.MESSAGE,
            this.loadedCommands,
            (commandEntry, compareEntry) -> commandEntry.getStructure().name().equalsIgnoreCase(compareEntry.getStructure().name())
        );
    }

    /**
     * Returns a new builder for constructing a {@code CommandHandler}.
     *
     * @param discordBot the bot the handler will belong to
     * @return a new builder instance
     */
    public static @NotNull Builder builder(@NotNull DiscordBot discordBot) {
        return new Builder(discordBot);
    }

    /**
     * Builds the list of {@link ApplicationCommandRequest} objects for a
     * given guild ID, including parent commands with subcommand groups,
     * standalone subcommands, and top-level commands with parameters.
     *
     * <p>
     * A guild ID of {@code -1} indicates global commands.
     *
     * @param guildId the guild ID to filter commands for, or {@code -1} for global
     * @return an unmodifiable list of application command requests
     */
    private @NotNull ConcurrentList<ApplicationCommandRequest> buildCommandRequests(long guildId) {
        return Stream.concat(
                Stream.concat(
                // Handle Parent Commands
                this.getSlashCommands()
                    .stream()
                    .filter(command -> StringUtil.isNotEmpty(command.getStructure().parent().name()))
                    .map(command -> command.getStructure().parent())
                    .filter(parent -> StringUtil.isNotEmpty(parent.name()))
                    .filter(StreamUtil.distinctByKey(Structure.Parent::name))
                    .map(parent -> this.buildParentCommand(parent)
                        // Handle SubCommand Groups
                        .addAllOptions(
                            this.getSlashCommands()
                                .stream()
                                .filter(command -> command.getStructure().parent().name().equalsIgnoreCase(parent.name()))
                                .map(command -> command.getStructure().group())
                                .filter(group -> StringUtil.isNotEmpty(group.name()))
                                .filter(StreamUtil.distinctByKey(Structure.Group::name))
                                .map(group -> this.buildGroup(parent, group, guildId))
                                .collect(Concurrent.toList())
                        )
                        // Handle SubCommands
                        .addAllOptions(
                            this.getSlashCommands()
                                .stream()
                                .filter(command -> parent.name().equalsIgnoreCase(command.getStructure().parent().name()))
                                .filter(command -> StringUtil.isEmpty(command.getStructure().group().name()))
                                .filter(command -> command.getStructure().guildId() == guildId)
                                .map(this::buildSubCommand)
                                .collect(Concurrent.toList())
                        )
                        .build())
                    .filter(commandRequest -> !commandRequest.options().isAbsent() && !commandRequest.options().get().isEmpty()),
                // Handle Top-Level Commands
                this.getSlashCommands()
                    .stream()
                    .filter(command -> StringUtil.isEmpty(command.getStructure().parent().name()))
                    .filter(command -> StringUtil.isEmpty(command.getStructure().group().name()))
                    .filter(command -> command.getStructure().guildId() == guildId)
                    .map(command -> {
                        String commandPath = pathOf(command);
                        return this.buildCommand(command)
                            // Handle Parameters
                            .addAllOptions(
                                command.getParameters()
                                    .stream()
                                    .map(parameter -> this.buildParameter(commandPath, parameter))
                                    .collect(Concurrent.toList())
                            )
                            .build();
                    })
                ),
                // Handle Context-Menu Commands (user + message) - registration previously omitted these
                Stream.concat(this.getUserCommands().stream(), this.getMessageCommands().stream())
                    .filter(command -> command.getStructure().guildId() == guildId)
                    .map(command -> this.buildCommand(command).build())
            )
            .map(ApplicationCommandRequest.class::cast)
            .collect(Concurrent.toUnmodifiableList());
    }

    /**
     * Returns the canonical lowercased dot-separated path for the given
     * command: {@code [parent.][group.]name}. The path is the natural key
     * used to look up localization overrides in {@link LocaleHandler}.
     *
     * @param command the command to compute a path for
     * @return the canonical command path
     */
    private static @NotNull String pathOf(@NotNull DiscordCommand command) {
        Structure structure = command.getStructure();
        StringBuilder builder = new StringBuilder();

        if (StringUtil.isNotEmpty(structure.parent().name()))
            builder.append(structure.parent().name().toLowerCase()).append('.');

        if (StringUtil.isNotEmpty(structure.group().name()))
            builder.append(structure.group().name().toLowerCase()).append('.');

        return builder.append(structure.name().toLowerCase()).toString();
    }

    /**
     * Creates a partial application command request builder for a parent
     * command defined by the given {@link Structure.Parent} annotation.
     *
     * @param parent the parent command structure
     * @return a pre-configured request builder
     */
    private @NotNull ImmutableApplicationCommandRequest.Builder buildParentCommand(@NotNull Structure.Parent parent) {
        String path = parent.name().toLowerCase();
        return ApplicationCommandRequest.builder()
            .type(ApplicationCommand.Type.CHAT_INPUT.getValue())
            .name(parent.name())
            .description(parent.description())
            .nameLocalizationsOrNull(nullIfEmpty(this.localeHandler.getCommandNameLocalizations(path)))
            .descriptionLocalizationsOrNull(nullIfEmpty(this.localeHandler.getCommandDescriptionLocalizations(path)));
    }

    /**
     * Creates a partial application command request builder for a
     * top-level command, populating type, name, description, NSFW flag,
     * permissions, integration types, and contexts.
     *
     * @param command the command to build a request for
     * @return a pre-configured request builder
     */
    private @NotNull ImmutableApplicationCommandRequest.Builder buildCommand(@NotNull DiscordCommand command) {
        String path = pathOf(command);
        ImmutableApplicationCommandRequest.Builder builder = ApplicationCommandRequest.builder()
            .type(command.getType().getValue())
            .name(command.getStructure().name())
            .nameLocalizationsOrNull(nullIfEmpty(this.localeHandler.getCommandNameLocalizations(path)))
            .nsfw(command.getStructure().nsfw())
            .defaultMemberPermissions(String.valueOf(PermissionSet.of(command.getStructure().userPermissions()).getRawValue()))
            .integrationTypes(DiscordCommand.Install.intValues(command.getStructure().integrations()))
            .contexts(DiscordCommand.Access.intValues(command.getStructure().contexts()));

        // Discord rejects a description (and description localizations) on USER/MESSAGE context-menu commands
        if (command.getType() == DiscordCommand.Type.CHAT_INPUT)
            builder.description(command.getStructure().description())
                .descriptionLocalizationsOrNull(nullIfEmpty(this.localeHandler.getCommandDescriptionLocalizations(path)));
        else
            builder.description("");

        return builder;
    }

    /**
     * Builds an {@link ApplicationCommandOptionData} of type
     * {@code SUB_COMMAND_GROUP} for the given parent/group pair, populating
     * nested subcommand options and localization maps.
     *
     * @param parent the owning parent command structure
     * @param group the subcommand group structure
     * @param guildId the guild ID being built for, or {@code -1} for global
     * @return the subcommand group option data
     */
    private @NotNull ApplicationCommandOptionData buildGroup(
        @NotNull Structure.Parent parent,
        @NotNull Structure.Group group,
        long guildId
    ) {
        String parentPath = parent.name().toLowerCase();
        String groupName = group.name().toLowerCase();

        return ApplicationCommandOptionData.builder()
            .type(ApplicationCommandOption.Type.SUB_COMMAND_GROUP.getValue())
            .name(groupName)
            .description(group.description())
            .nameLocalizationsOrNull(nullIfEmpty(this.localeHandler.getOptionNameLocalizations(parentPath, groupName)))
            .descriptionLocalizationsOrNull(nullIfEmpty(this.localeHandler.getOptionDescriptionLocalizations(parentPath, groupName)))
            .addAllOptions(
                this.getSlashCommands()
                    .stream()
                    .filter(command -> parent.name().equalsIgnoreCase(command.getStructure().parent().name()))
                    .filter(command -> group.name().equalsIgnoreCase(command.getStructure().group().name()))
                    .filter(command -> command.getStructure().guildId() == guildId)
                    .map(this::buildSubCommand)
                    .collect(Concurrent.toList())
            )
            .build();
    }

    /**
     * Builds an {@link ApplicationCommandOptionData} of type
     * {@code SUB_COMMAND} for the given slash command, including its
     * parameters as nested options.
     *
     * @param command the slash command to represent as a subcommand option
     * @return the subcommand option data
     */
    private @NotNull ApplicationCommandOptionData buildSubCommand(@NotNull DiscordCommand<SlashCommandContext> command) {
        String commandPath = pathOf(command);
        int lastDot = commandPath.lastIndexOf('.');
        String parentPath = lastDot > 0 ? commandPath.substring(0, lastDot) : commandPath;
        String subName = command.getStructure().name().toLowerCase();

        return ApplicationCommandOptionData.builder()
            .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
            .name(command.getStructure().name())
            .description(command.getStructure().description())
            .nameLocalizationsOrNull(nullIfEmpty(this.localeHandler.getOptionNameLocalizations(parentPath, subName)))
            .descriptionLocalizationsOrNull(nullIfEmpty(this.localeHandler.getOptionDescriptionLocalizations(parentPath, subName)))
            .addAllOptions(
                command.getParameters()
                    .stream()
                    .map(parameter -> this.buildParameter(commandPath, parameter))
                    .collect(Concurrent.toList())
            )
            .build();
    }

    /**
     * Converts a {@link Parameter} into an {@link ApplicationCommandOptionData},
     * mapping type, name, description, required flag, channel type
     * constraints, size/length limits, autocomplete flag, and static
     * choices. Localization overrides for the parameter and each choice
     * are resolved from the {@link LocaleHandler}.
     *
     * @param commandPath the canonical path of the owning command,
     *                    used as the prefix for localization lookups
     * @param parameter the command parameter to convert
     * @return the application command option data
     */
    private @NotNull ApplicationCommandOptionData buildParameter(@NotNull String commandPath, @NotNull Parameter parameter) {
        return ApplicationCommandOptionData.builder()
            .type(parameter.getType().getOptionType().getValue())
            .name(parameter.getName())
            .description(parameter.getDescription())
            .nameLocalizationsOrNull(nullIfEmpty(this.localeHandler.getOptionNameLocalizations(commandPath, parameter.getName())))
            .descriptionLocalizationsOrNull(nullIfEmpty(this.localeHandler.getOptionDescriptionLocalizations(commandPath, parameter.getName())))
            .required(parameter.isRequired())
            .channelTypes(
                parameter.getChannelTypes()
                    .stream()
                    .map(Channel.Type::getValue)
                    .collect(Concurrent.toList())
            )
            .minValue(parameter.getSizeLimit().getMinimum())
            .maxValue(parameter.getSizeLimit().getMaximum())
            .minLength(parameter.getLengthLimit().getMinimum())
            .maxLength(parameter.getLengthLimit().getMaximum())
            .autocomplete(parameter.isAutocompleting())
            .choices(
                parameter.getChoices()
                    .stream()
                    .map(entry -> ApplicationCommandOptionChoiceData.builder()
                        .name(entry.getKey())
                        .value(entry.getValue())
                        .nameLocalizationsOrNull(nullIfEmpty(this.localeHandler.getChoiceNameLocalizations(commandPath, parameter.getName(), entry.getKey())))
                        .build()
                    )
                    .collect(Concurrent.toList())
            )
            .build();
    }

    /**
     * Returns {@code null} if the given map is empty, otherwise returns the
     * map itself. Used to pass localization overrides through the
     * Discord4J {@code *OrNull} builder methods without sending empty
     * maps on commands with no translations.
     *
     * @param map the localization map
     * @return the same map if non-empty, otherwise {@code null}
     */
    private static Map<String, String> nullIfEmpty(@NotNull Map<String, String> map) {
        return map.isEmpty() ? null : map;
    }

    /**
     * Returns all loaded commands whose Discord-assigned application
     * command ID matches the given value.
     *
     * @param commandId the Discord application command ID
     * @return an unmodifiable list of matching commands
     */
    public @NotNull ConcurrentList<DiscordCommand> getCommandsById(long commandId) {
        return this.getLoadedCommands()
            .stream()
            .filter(command -> Long.valueOf(commandId).equals(this.getCommandId(command.getClass())))
            .collect(Concurrent.toUnmodifiableList());
    }

    /**
     * Returns the Discord-assigned application command ID for the given
     * command class, or {@code null} if the class has not been registered.
     *
     * @param commandClass the command class to look up
     * @return the application command ID, or {@code null}
     */
    public Long getCommandId(@NotNull Class<? extends DiscordCommand> commandClass) {
        return this.commandIds.get(commandClass);
    }

    /**
     * Returns all loaded commands matching the given name and type.
     *
     * <p>
     * For {@link DiscordCommand.Type#CHAT_INPUT CHAT_INPUT} commands, the
     * name is matched against the parent name (if present) or the command
     * name. For other types, an exact match on the command name is required.
     *
     * @param name the command or parent name to match
     * @param type the command type to filter by
     * @return an unmodifiable list of matching commands
     */
    private @NotNull ConcurrentList<DiscordCommand> getCommandReferences(@NotNull String name, @NotNull DiscordCommand.Type type) {
        return this.getLoadedCommands()
            .stream()
            .filter(command -> command.getType() == type)
            .filter(command -> {
                if (command.getType() == DiscordCommand.Type.CHAT_INPUT) {
                    if (StringUtil.isNotEmpty(command.getStructure().parent().name()))
                        return command.getStructure().parent().name().equalsIgnoreCase(name);
                    else
                        return command.getStructure().name().equalsIgnoreCase(name);
                } else
                    return command.getStructure().name().equals(name);
            })
            .collect(Concurrent.toUnmodifiableList());
    }

    /**
     * Filters commands by the given type and removes duplicates detected
     * by the provided predicate, logging conflicts.
     *
     * @param type the command type to filter by
     * @param commands the full list of validated commands
     * @param filter a predicate that returns {@code true} when two commands
     *               conflict (same name/parent/group combination)
     * @param <A> the application command interaction event type
     * @param <C> the command context type
     * @return an unmodifiable list of non-conflicting commands of the given type
     */
    @SuppressWarnings("all")
    private <A extends ApplicationCommandInteractionEvent, C extends CommandContext<A>> @NotNull ConcurrentList<DiscordCommand<C>> retrieveTypedCommands(
        @NotNull DiscordCommand.Type type,
        @NotNull ConcurrentList<DiscordCommand> commands,
        @NotNull BiPredicate<DiscordCommand, DiscordCommand> filter
    ) {
        return commands.stream()
            .filter(command -> command.getType() == type)
            .filter(command -> {
                Optional<DiscordCommand> commandOverlap = commands.stream()
                    .filter(compareEntry -> !command.equals(compareEntry))
                    .filter(compareEntry -> filter.test(command, compareEntry)) // Compare Commands
                    .findAny();

                if (commandOverlap.isPresent()) {
                    this.getLog().info("Command '{}' conflicts with '{}'!", command.getStructure().name(), commandOverlap.get().getStructure().name());
                    return false;
                }

                return true;
            })
            .map(command -> (DiscordCommand<C>) command)
            .collect(Concurrent.toUnmodifiableList());
    }

    /**
     * Bulk-overwrites both global and guild-specific application commands
     * with the Discord API.
     *
     * @return a mono that completes when all commands have been registered
     */
    public @NotNull Mono<Void> updateApplicationCommands() {
        return this.updateGlobalApplicationCommands()
            .then(this.updateGuildApplicationCommands());
    }

    /**
     * Bulk-overwrites all global application commands with the Discord
     * API and updates the internal command ID mapping.
     *
     * @return a mono that completes when global commands have been registered
     */
    public @NotNull Mono<Void> updateGlobalApplicationCommands() {
        return this.getDiscordBot()
            .getGateway()
            .getRestClient()
            .getApplicationService()
            .bulkOverwriteGlobalApplicationCommand(
                this.getDiscordBot().getClientId().asLong(),
                this.buildCommandRequests(-1)
            )
            .doOnNext(commandData -> this.getCommandReferences(commandData.name(), DiscordCommand.Type.of(commandData.type().toOptional().orElse(-1)))
                .forEach(command -> this.commandIds.put(command.getClass(), commandData.id().asLong()))
            )
            .then();
    }

    /**
     * Bulk-overwrites application commands for all guilds that have at
     * least one guild-specific command registered.
     *
     * @return a mono that completes when all guild commands have been registered
     */
    public @NotNull Mono<Void> updateGuildApplicationCommands() {
        return Flux.fromIterable(this.getLoadedCommands())
            .filter(command -> command.getStructure().guildId() > 0)
            .map(DiscordCommand::getStructure)
            .map(Structure::guildId)
            .filter(guildId -> guildId > 0)
            .distinct()
            .flatMap(this::updateGuildApplicationCommands)
            .then();
    }

    /**
     * Bulk-overwrites application commands for a specific guild and
     * updates the internal command ID mapping.
     *
     * @param guildId the Discord guild ID to register commands for
     * @return a mono that completes when the guild commands have been registered
     */
    public @NotNull Mono<Void> updateGuildApplicationCommands(long guildId) {
        return this.getDiscordBot()
            .getGateway()
            .getRestClient()
            .getApplicationService()
            .bulkOverwriteGuildApplicationCommand(
                this.getDiscordBot().getClientId().asLong(),
                guildId,
                this.buildCommandRequests(guildId)
            )
            .doOnNext(commandData -> this.getCommandReferences(commandData.name(), DiscordCommand.Type.of(commandData.type().toOptional().orElse(-1)))
                .forEach(command -> this.commandIds.put(command.getClass(), commandData.id().asLong()))
            )
            .then();
    }

    /**
     * Validates and instantiates the given set of command classes,
     * filtering out those missing the {@link Structure} annotation or
     * having invalid command names.
     *
     * @param discordBot the bot instance to pass to command constructors
     * @param commands the set of command classes to validate
     * @return a list of successfully validated and instantiated commands
     */
    private @NotNull ConcurrentList<DiscordCommand> validateCommands(@NotNull DiscordBot discordBot, @NotNull ConcurrentSet<Class<DiscordCommand>> commands) {
        return commands.stream()
            .map(commandClass -> Pair.of(
                commandClass,
                getAnnotation(Structure.class, commandClass)
            ))
            .filter(commandLink -> {
                if (commandLink.right().isEmpty()) {
                    this.getLog().warn("'{}' is missing the @CommandStructure annotation, ignoring.", commandLink.left().getName());
                    return false;
                }

                return true;
            })
            .filter(StreamUtil.distinctByKey(Pair::right))
            .map(commandLink -> Pair.of(
                commandLink.left(),
                new Reflection<>(commandLink.left()).newInstance(discordBot)
            ))
            .filter(commandEntry -> {
                if (!validCommandPattern.matcher(commandEntry.right().getStructure().name()).matches()) {
                    this.getLog().info("The command name of '{}' ('{}') must only contain english characters!", commandEntry.left().getName(), commandEntry.right().getStructure().name());
                    return false;
                }

                return true;
            })
            .map(Pair::right)
            .collect(Concurrent.toList());
    }

    /**
     * Builder for constructing a {@link CommandHandler} with a set of
     * command classes to validate and register.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {

        private final DiscordBot discordBot;
        private final ConcurrentSet<Class<DiscordCommand>> commands = Concurrent.newSet();
        private LocaleHandler localeHandler;

        /**
         * Adds the given command classes to the set of commands to register.
         *
         * @param commands the command classes to add
         * @return this builder for chaining
         */
        public Builder withCommands(@NotNull Class<DiscordCommand>... commands) {
            return this.withCommands(Arrays.asList(commands));
        }

        /**
         * Adds the given command classes to the set of commands to register.
         *
         * @param commands the command classes to add
         * @return this builder for chaining
         */
        public Builder withCommands(@NotNull Iterable<Class<DiscordCommand>> commands) {
            commands.forEach(this.commands::add);
            return this;
        }

        /**
         * Sets the locale handler consulted while building every command spec.
         *
         * @param localeHandler the locale handler
         * @return this builder for chaining
         */
        public Builder withLocaleHandler(@NotNull LocaleHandler localeHandler) {
            this.localeHandler = localeHandler;
            return this;
        }

        /**
         * Builds a new {@link CommandHandler} with the accumulated command
         * classes.
         *
         * @return the constructed command handler
         * @throws IllegalStateException if no {@link LocaleHandler} was supplied
         */
        public @NotNull CommandHandler build() {
            if (this.localeHandler == null)
                throw new IllegalStateException("LocaleHandler is required");

            return new CommandHandler(
                this.discordBot,
                this.commands,
                this.localeHandler
            );
        }

    }

}
