package dev.sbs.discordapi.util;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.collection.concurrent.ConcurrentSet;
import dev.sbs.api.util.mutable.pair.Pair;
import dev.sbs.api.util.stream.StreamUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.CommandId;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.command.reference.CommandReference;
import dev.sbs.discordapi.command.reference.MessageCommandReference;
import dev.sbs.discordapi.command.reference.SlashCommandReference;
import dev.sbs.discordapi.command.reference.UserCommandReference;
import dev.sbs.discordapi.context.deferrable.application.CommandContext;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.object.command.ApplicationCommand;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.channel.Channel;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import discord4j.rest.util.Permission;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Log4j2
@SuppressWarnings("rawtypes")
public class DiscordCommandRegistrar extends DiscordReference {

    private static final Pattern validCommandPattern = Pattern.compile("^[\\w-]{1,32}$");
    private final @NotNull ConcurrentMap<Class<? extends CommandReference>, Long> commandIds = Concurrent.newMap();
    @Getter private final @NotNull ConcurrentList<CommandReference<?>> loadedCommands;
    @Getter private final @NotNull ConcurrentList<SlashCommandReference> slashCommands;
    @Getter private final @NotNull ConcurrentList<UserCommandReference> userCommands;
    @Getter private final @NotNull ConcurrentList<MessageCommandReference> messageCommands;

    DiscordCommandRegistrar(
        @NotNull DiscordBot discordBot,
        @NotNull ConcurrentSet<Class<? extends CommandReference>> commands
    ) {
        super(discordBot);

        this.getLog().info("Validating Commands");
        ConcurrentList<CommandReference> initializedCommands = this.validateCommands(discordBot, commands);

        this.getLog().info("Retrieving Commands");
        this.getLog().debug("Retrieving Slash Commands");
        this.slashCommands = this.retrieveTypedCommands(
            SlashCommandReference.class,
            initializedCommands,
            (commandEntry, compareEntry) -> Objects.equals(
                commandEntry.getParent(),
                compareEntry.getParent()
            ) && commandEntry.getName().equalsIgnoreCase(compareEntry.getName())
        );

        this.getLog().debug("Retrieving User Commands");
        this.userCommands = this.retrieveTypedCommands(
            UserCommandReference.class,
            initializedCommands,
            (commandEntry, compareEntry) -> commandEntry.getName().equalsIgnoreCase(compareEntry.getName())
        );

        this.getLog().debug("Retrieving Message Commands");
        this.messageCommands = this.retrieveTypedCommands(
            MessageCommandReference.class,
            initializedCommands,
            (commandEntry, compareEntry) -> commandEntry.getName().equalsIgnoreCase(compareEntry.getName())
        );

        ConcurrentList<CommandReference<?>> commandTree = Concurrent.newList();
        commandTree.addAll(this.slashCommands);
        commandTree.addAll(this.userCommands);
        commandTree.addAll(this.messageCommands);
        this.loadedCommands = commandTree.toUnmodifiableList();

        /* // Create Missing Command Config
        if (commandConfigModel.isEmpty()) {
            CommandConfigSqlModel newCommandConfigModel = new CommandConfigSqlModel();
            newCommandConfigModel.setUniqueId(commandId);
            newCommandConfigModel.setName(commandId.toString().substring(0, 8));
            newCommandConfigModel.setDescription("*<missing description>*");
            newCommandConfigModel.setDeveloperOnly(true);
            newCommandConfigModel.setEnabled(true);
            newCommandConfigModel.setInheritingPermissions(true);
            this.getLog().info("Creating new CommandConfigModel for ''{0}''.", commandLink.getLeft().getName());
            commandConfigModel = Optional.of(((SqlRepository<CommandConfigSqlModel>) SimplifiedApi.getRepositoryOf(CommandConfigSqlModel.class)).save(newCommandConfigModel));
        }*/
    }

    public static Builder builder(@NotNull DiscordBot discordBot) {
        return new Builder(discordBot);
    }

    private ConcurrentList<ApplicationCommandRequest> buildCommandRequests(long guildId) {
        return Stream.concat(
                // Handle Parent Commands
                this.getSlashCommands()
                    .stream()
                    .flatMap(command -> command.getParent().stream())
                    .filter(StreamUtil.distinctByKey(SlashCommandReference.Parent::getName))
                    .map(parent -> this.buildCommand(parent)
                        // Handle SubCommand Groups
                        .addAllOptions(
                            this.getSlashCommands()
                                .stream()
                                .filter(command -> command.getParent().map(compare -> parent.getName().equals(compare.getName())).orElse(false))
                                .flatMap(command -> command.getGroup().stream())
                                .filter(StreamUtil.distinctByKey(SlashCommandReference.Group::getName))
                                .map(group -> ApplicationCommandOptionData.builder()
                                    .type(ApplicationCommandOption.Type.SUB_COMMAND_GROUP.getValue())
                                    .name(group.getName().toLowerCase())
                                    .description(group.getDescription())
                                    .addAllOptions(
                                        this.getSlashCommands()
                                            .stream()
                                            .filter(command -> command.getParent().isPresent())
                                            .filter(command -> parent.getName().equals(command.getParent().get().getName()))
                                            .filter(command -> command.getGroup().isPresent())
                                            .filter(command -> group.getName().equals(command.getGroup().get().getName()))
                                            .filter(command -> command.getGuildId() == guildId)
                                            .map(this::buildSubCommand)
                                            .collect(Concurrent.toList())
                                    )
                                    .build()
                                )
                                .collect(Concurrent.toList())
                        )
                        // Handle SubCommands
                        .addAllOptions(
                            this.getSlashCommands()
                                .stream()
                                .filter(command -> command.getParent().isPresent())
                                .filter(command -> parent.getName().equals(command.getParent().get().getName()))
                                .filter(command -> command.getGroup().isEmpty())
                                .filter(command -> command.getGuildId() == guildId)
                                .map(this::buildSubCommand)
                                .collect(Concurrent.toList())
                        )
                        .build())
                    .filter(commandRequest -> !commandRequest.options().isAbsent() && !commandRequest.options().get().isEmpty()),
                // Handle Top-Level Commands
                this.getSlashCommands()
                    .stream()
                    .filter(command -> command.getParent().isEmpty())
                    .filter(command -> command.getGuildId() == guildId)
                    .map(command -> this.buildCommand(command)
                        // Handle Parameters
                        .addAllOptions(
                            command.getParameters()
                                .stream()
                                .map(this::buildParameter)
                                .collect(Concurrent.toList())
                        )
                        .build()
                    )
            )
            .map(ApplicationCommandRequest.class::cast)
            .collect(Concurrent.toList())
            .toUnmodifiableList();
    }

    private @NotNull ImmutableApplicationCommandRequest.Builder buildCommand(@NotNull SlashCommandReference.Parent parent) {
        return ApplicationCommandRequest.builder()
            .type(ApplicationCommand.Type.CHAT_INPUT.getValue())
            .name(parent.getName())
            .description(parent.getDescription());
    }

    private @NotNull ImmutableApplicationCommandRequest.Builder buildCommand(@NotNull CommandReference<?> command) {
        return ApplicationCommandRequest.builder()
            .type(command.getType().getValue())
            .name(command.getName())
            .description(command.getDescription())
            .dmPermission(command.isAvailableInPrivateChannels())
            .defaultMemberPermissions(String.valueOf(
                command.getDefaultPermissions()
                    .stream()
                    .mapToLong(Permission::getValue)
                    .reduce(0, (a, b) -> a | b)
            ));
    }

    private @NotNull ApplicationCommandOptionData buildSubCommand(@NotNull SlashCommandReference command) {
        return ApplicationCommandOptionData.builder()
            .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
            .name(command.getName())
            .description(command.getDescription())
            .addAllOptions(
                command.getParameters()
                    .stream()
                    .map(this::buildParameter)
                    .collect(Concurrent.toList())
            )
            .build();
    }

    private @NotNull ApplicationCommandOptionData buildParameter(@NotNull Parameter parameter) {
        return ApplicationCommandOptionData.builder()
            .type(parameter.getType().getOptionType().getValue())
            .name(parameter.getName())
            .description(parameter.getDescription())
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
                        .build()
                    )
                    .collect(Concurrent.toList())
            )
            .build();
    }

    public Mono<Void> updateApplicationCommands() {
        return this.getDiscordBot()
            .getGateway()
            .getRestClient()
            .getApplicationService()
            .bulkOverwriteGlobalApplicationCommand(
                this.getDiscordBot().getClientId().asLong(),
                this.buildCommandRequests(-1)
            )
            .doOnNext(commandData -> this.getCommandReferences(commandData.name(), CommandReference.Type.of(commandData.type().toOptional().orElse(-1)))
                .forEach(command -> this.commandIds.put(command.getClass(), commandData.id().asLong()))
            )
            .thenMany(
                Flux.fromIterable(this.getLoadedCommands())
                    .map(CommandReference::getGuildId)
                    .filter(guildId -> guildId > 0)
                    .distinct()
                    .flatMap(guildId -> this.getDiscordBot()
                        .getGateway()
                        .getRestClient()
                        .getApplicationService()
                        .bulkOverwriteGuildApplicationCommand(
                            this.getDiscordBot().getClientId().asLong(),
                            guildId,
                            this.buildCommandRequests(guildId)
                        )
                        .doOnNext(commandData -> this.getCommandReferences(commandData.name(), CommandReference.Type.of(commandData.type().toOptional().orElse(-1)))
                            .forEach(command -> this.commandIds.put(command.getClass(), commandData.id().asLong()))
                        )
                    )
            )
            .then();
    }

    public long getApiCommandId(@NotNull Class<? extends CommandReference> commandClass) {
        return this.commandIds.get(commandClass);
    }

    private @NotNull ConcurrentList<CommandReference<?>> getCommandReferences(@NotNull String name, @NotNull CommandReference.Type type) {
        return this.getLoadedCommands()
            .stream()
            .filter(command -> command.getType() == type)
            .filter(command -> {
                if (command instanceof SlashCommandReference slashCommand) {
                    return slashCommand.getParent()
                        .map(SlashCommandReference.Parent::getName)
                        .orElse(slashCommand.getName())
                        .equals(name);
                } else
                    return command.getName().equals(name);
            })
            .collect(Concurrent.toUnmodifiableList());
    }

    private <
        A extends ApplicationCommandInteractionEvent,
        C extends CommandContext<A>,
        T extends CommandReference<C>
        > @NotNull ConcurrentList<T> retrieveTypedCommands(
        @NotNull Class<T> referenceType,
        @NotNull ConcurrentList<CommandReference> commands,
        @NotNull BiPredicate<T, T> filter
    ) {
        ConcurrentList<T> typedCommands = commands.stream()
            .filter(reference -> referenceType.isAssignableFrom(reference.getClass()))
            .map(referenceType::cast)
            .collect(Concurrent.toList());

        return typedCommands.stream()
            .filter(commandEntry -> {
                Optional<T> commandOverlap = typedCommands.stream()
                    .filter(compareEntry -> !commandEntry.equals(compareEntry))
                    .filter(compareEntry -> filter.test(commandEntry, compareEntry)) // Compare Commands
                    .findAny();

                if (commandOverlap.isPresent()) {
                    this.getLog().info("Command '{}' conflicts with '{}'!", commandEntry.getName(), commandOverlap.get().getName());
                    return false;
                }

                return true;
            })
            .collect(Concurrent.toUnmodifiableList());
    }

    private @NotNull ConcurrentList<CommandReference> validateCommands(@NotNull DiscordBot discordBot, @NotNull ConcurrentSet<Class<? extends CommandReference>> commands) {
        return commands.stream()
            .map(commandClass -> Pair.of(
                commandClass,
                getAnnotation(CommandId.class, commandClass)
            ))
            .filter(commandLink -> {
                if (commandLink.getRight().isEmpty()) {
                    this.getLog().warn("'{}' is missing the @CommandId annotation, ignoring.", commandLink.getLeft().getName());
                    return false;
                }

                return true;
            })
            .filter(StreamUtil.distinctByKey(Pair::getRight))
            .map(commandLink -> Pair.of(
                commandLink.getLeft(),
                Reflection.of(commandLink.getLeft()).newInstance(discordBot)
            ))
            .filter(commandEntry -> {
                if (!validCommandPattern.matcher(commandEntry.getRight().getName()).matches()) {
                    this.getLog().info("The command name of '{}' ('{}') must only contain english characters!", commandEntry.getLeft().getName(), commandEntry.getRight().getName());
                    return false;
                }

                return true;
            })
            .map(Pair::getRight)
            .collect(Concurrent.toList());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements dev.sbs.api.util.builder.Builder<DiscordCommandRegistrar> {

        private final DiscordBot discordBot;
        private final ConcurrentSet<Class<? extends CommandReference>> commands = Concurrent.newSet();

        public Builder withCommands(@NotNull Class<? extends CommandReference<?>>... commands) {
            return this.withCommands(Arrays.asList(commands));
        }

        public Builder withCommands(@NotNull Iterable<Class<? extends CommandReference>> commands) {
            commands.forEach(this.commands::add);
            return this;
        }

        @Override
        public @NotNull DiscordCommandRegistrar build() {
            return new DiscordCommandRegistrar(
                this.discordBot,
                this.commands
            );
        }

    }

}
