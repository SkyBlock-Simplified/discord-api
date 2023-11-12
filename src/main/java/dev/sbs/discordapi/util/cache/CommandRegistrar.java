package dev.sbs.discordapi.util.cache;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.collection.concurrent.ConcurrentSet;
import dev.sbs.api.util.data.tuple.Pair;
import dev.sbs.api.util.helper.StreamUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.impl.DiscordCommand;
import dev.sbs.discordapi.command.impl.SlashCommand;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.command.reference.CommandReference;
import dev.sbs.discordapi.command.reference.SlashCommandReference;
import dev.sbs.discordapi.util.base.DiscordHelper;
import discord4j.core.object.command.ApplicationCommand;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class CommandRegistrar extends DiscordHelper {

    private static final Pattern validCommandPattern = Pattern.compile("^[\\w-]{1,32}$");
    private final @NotNull ConcurrentMap<Long, Class<? extends CommandReference>> commandIds = Concurrent.newMap();
    @Getter private final @NotNull ConcurrentMap<Class<? extends CommandReference>, CommandReference> loadedCommands;
    @Getter private final @NotNull ConcurrentMap<Class<? extends SlashCommandReference>, SlashCommand> slashCommands;

    CommandRegistrar(
        @NotNull DiscordBot discordBot,
        @NotNull ConcurrentSet<Class<? extends CommandReference>> commands
    ) {
        super(discordBot);

        this.getLog().info("Validating Commands");
        ConcurrentMap<Class<? extends CommandReference>, CommandReference> initializedCommands = this.validateCommands(discordBot, commands);

        this.slashCommands = this.retrieveTypedCommands(
            SlashCommandReference.class,
            SlashCommand.class,
            initializedCommands,
            (commandEntry, compareEntry) -> Objects.equals(
                commandEntry.getValue().getParent(),
                compareEntry.getValue().getParent()
            ) && commandEntry.getValue()
                .getName()
                .equalsIgnoreCase(compareEntry.getValue().getName())
        );

        this.getLog().info("Building Command Tree");
        ConcurrentMap<Class<? extends CommandReference>, CommandReference> commandTree = Concurrent.newMap();
        commandTree.putAll(this.slashCommands);
        this.loadedCommands = Concurrent.newUnmodifiableMap(commandTree);

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
                    .map(Map.Entry::getValue)
                    .flatMap(command -> command.getParent().stream())
                    .distinct()
                    .map(parent -> this.buildCommand(parent)
                        // Handle SubCommand Groups
                        .addAllOptions(
                            this.getSlashCommands()
                                .stream()
                                .map(Map.Entry::getValue)
                                .filter(command -> command.getParent().map(compare -> parent.getName().equals(compare.getName())).orElse(false))
                                .flatMap(command -> command.getGroup().stream())
                                .distinct()
                                .map(group -> ApplicationCommandOptionData.builder()
                                    .type(ApplicationCommandOption.Type.SUB_COMMAND_GROUP.getValue())
                                    .name(group.getName().toLowerCase())
                                    .description(group.getDescription())
                                    .addAllOptions(
                                        this.getSlashCommands()
                                            .stream()
                                            .map(Map.Entry::getValue)
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
                                .map(Map.Entry::getValue)
                                .filter(command -> command.getParent().isPresent())
                                .filter(command -> parent.getName().equals(command.getParent().get().getName()))
                                .filter(command -> command.getGroup().isEmpty())
                                .filter(command -> command.getGuildId() == guildId)
                                .map(this::buildSubCommand)
                                .collect(Concurrent.toList())
                        )
                        .build()
                    )
                    .filter(commandRequest -> !commandRequest.options().isAbsent() && !commandRequest.options().get().isEmpty()),
                // Handle Top-Level Commands
                this.getSlashCommands()
                    .stream()
                    .map(Map.Entry::getValue)
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

    private @NotNull ImmutableApplicationCommandRequest.Builder buildCommand(@NotNull CommandReference.Parent parent) {
        return ApplicationCommandRequest.builder()
            .name(parent.getName())
            .description(parent.getDescription())
            .type(ApplicationCommand.Type.CHAT_INPUT.getValue());
    }

    private @NotNull ImmutableApplicationCommandRequest.Builder buildCommand(@NotNull CommandReference command) {
        return ApplicationCommandRequest.builder()
            .name(command.getName())
            .description(command.getDescription())
            .type(command.getType().getValue());
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
            .doOnNext(commandData -> {
                // TODO: Back-update commands with their commandData.id()
                return;
            })
            .thenMany(
                Flux.fromIterable(this.getLoadedCommands())
                    .filter(commandEntry -> commandEntry.getValue().getGuildId() > 0)
                    .flatMap(entry -> this.getDiscordBot()
                        .getGateway()
                        .getRestClient()
                        .getApplicationService()
                        .bulkOverwriteGuildApplicationCommand(
                            this.getDiscordBot().getClientId().asLong(),
                            entry.getValue().getGuildId(),
                            this.buildCommandRequests(entry.getValue().getGuildId())
                        )
                        .doOnNext(commandData -> {
                            // TODO: Back-update commands with their commandData.id()
                            return;
                        })
                    )
            )
            .then();
    }

    private <T extends CommandReference, I extends DiscordCommand<?, ?>> @NotNull ConcurrentMap<Class<? extends T>, I> retrieveTypedCommands(
        @NotNull Class<T> referenceType,
        @NotNull Class<I> instanceType,
        @NotNull ConcurrentMap<Class<? extends CommandReference>, CommandReference> commands,
        @NotNull BiFunction<Map.Entry<Class<T>, I>, Map.Entry<Class<T>, I>, Boolean> filter
    ) {
        ConcurrentMap<Class<T>, I> typedCommands = commands.stream()
            .filter(commandEntry -> referenceType.isAssignableFrom(commandEntry.getKey()))
            .map(commandEntry -> Pair.of(commandEntry.getKey(), instanceType.cast(commandEntry.getValue())))
            .collect(Concurrent.toMap());

        return Concurrent.newUnmodifiableMap(
            typedCommands.stream()
                .filter(commandEntry -> {
                    Optional<Map.Entry<Class<T>, I>> commandOverlap = typedCommands.stream()
                        .filter(compareEntry -> !commandEntry.getKey().equals(compareEntry.getKey()))
                        .filter(compareEntry -> filter.apply(commandEntry, compareEntry)) // Compare Commands
                        .findAny();

                    if (commandOverlap.isPresent()) {
                        this.getLog().info("Command '{}' conflicts with '{}'!", commandEntry.getValue().getName(), commandOverlap.get().getValue().getName());
                        return false;
                    }

                    return true;
                })
                .collect(Concurrent.toMap())
        );
    }

    private @NotNull ConcurrentMap<Class<? extends CommandReference>, CommandReference> validateCommands(@NotNull DiscordBot discordBot, @NotNull ConcurrentSet<Class<? extends CommandReference>> commands) {
        return commands.stream()
            .map(commandClass -> Pair.of(
                commandClass,
                getCommandId(commandClass)
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
            .collect(Concurrent.toMap());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements dev.sbs.api.util.builder.Builder<CommandRegistrar> {

        private final DiscordBot discordBot;
        private final ConcurrentSet<Class<? extends CommandReference>> commands = Concurrent.newSet();

        public Builder withCommand(@NotNull Class<? extends CommandReference> command) {
            this.commands.add(command);
            return this;
        }

        public Builder withCommands(@NotNull Iterable<Class<? extends CommandReference>> commands) {
            commands.forEach(this::withCommand);
            return this;
        }

        @Override
        public CommandRegistrar build() {
            return new CommandRegistrar(
                this.discordBot,
                this.commands
            );
        }

    }

}
