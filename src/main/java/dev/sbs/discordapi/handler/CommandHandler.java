package dev.sbs.discordapi.handler;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.collection.concurrent.ConcurrentMap;
import dev.sbs.api.collection.concurrent.ConcurrentSet;
import dev.sbs.api.mutable.pair.Pair;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.stream.StreamUtil;
import dev.sbs.api.util.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.CommandStructure;
import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.command.MessageCommand;
import dev.sbs.discordapi.command.SlashCommand;
import dev.sbs.discordapi.command.UserCommand;
import dev.sbs.discordapi.command.context.AccessContext;
import dev.sbs.discordapi.command.context.InstallContext;
import dev.sbs.discordapi.command.context.TypeContext;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.context.deferrable.command.CommandContext;
import dev.sbs.discordapi.util.DiscordReference;
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
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Log4j2
@SuppressWarnings("rawtypes")
public class CommandHandler extends DiscordReference {

    private static final Pattern validCommandPattern = Pattern.compile("^[\\w-]{1,32}$");
    private final @NotNull ConcurrentMap<Class<? extends DiscordCommand>, Long> commandIds = Concurrent.newMap();
    @Getter private final @NotNull ConcurrentList<DiscordCommand> loadedCommands;
    @Getter private final @NotNull ConcurrentList<SlashCommand> slashCommands;
    @Getter private final @NotNull ConcurrentList<UserCommand> userCommands;
    @Getter private final @NotNull ConcurrentList<MessageCommand> messageCommands;

    CommandHandler(
        @NotNull DiscordBot discordBot,
        @NotNull ConcurrentSet<Class<? extends DiscordCommand>> commands
    ) {
        super(discordBot);

        this.getLog().info("Validating Commands");
        ConcurrentList<DiscordCommand> initializedCommands = this.validateCommands(discordBot, commands);

        this.getLog().info("Retrieving Commands");
        this.getLog().debug("Retrieving Slash Commands");
        this.slashCommands = this.retrieveTypedCommands(
            SlashCommand.class,
            initializedCommands,
            (commandEntry, compareEntry) -> Objects.equals(
                commandEntry.getStructure().parent(),
                compareEntry.getStructure().parent()
            ) && Objects.equals(
                commandEntry.getStructure().group(),
                compareEntry.getStructure().group()
            ) && commandEntry.getStructure().name().equalsIgnoreCase(compareEntry.getStructure().name())
        );

        this.getLog().debug("Retrieving User Commands");
        this.userCommands = this.retrieveTypedCommands(
            UserCommand.class,
            initializedCommands,
            (commandEntry, compareEntry) -> commandEntry.getStructure().name().equalsIgnoreCase(compareEntry.getStructure().name())
        );

        this.getLog().debug("Retrieving Message Commands");
        this.messageCommands = this.retrieveTypedCommands(
            MessageCommand.class,
            initializedCommands,
            (commandEntry, compareEntry) -> commandEntry.getStructure().name().equalsIgnoreCase(compareEntry.getStructure().name())
        );

        ConcurrentList<DiscordCommand> commandTree = Concurrent.newList();
        commandTree.addAll(this.slashCommands);
        commandTree.addAll(this.userCommands);
        commandTree.addAll(this.messageCommands);
        this.loadedCommands = commandTree.toUnmodifiableList();
    }

    public static Builder builder(@NotNull DiscordBot discordBot) {
        return new Builder(discordBot);
    }

    private ConcurrentList<ApplicationCommandRequest> buildCommandRequests(long guildId) {
        return Stream.concat(
                // Handle Parent Commands
                this.getSlashCommands()
                    .stream()
                    .filter(command -> StringUtil.isNotEmpty(command.getStructure().parent().name()))
                    .map(command -> command.getStructure().parent())
                    .filter(parent -> StringUtil.isNotEmpty(parent.name()))
                    .filter(StreamUtil.distinctByKey(CommandStructure.Parent::name))
                    .map(parent -> this.buildParentCommand(parent)
                        // Handle SubCommand Groups
                        .addAllOptions(
                            this.getSlashCommands()
                                .stream()
                                .filter(command -> command.getStructure().parent().name().equalsIgnoreCase(parent.name()))
                                .map(command -> command.getStructure().group())
                                .filter(group -> StringUtil.isNotEmpty(group.name()))
                                .filter(StreamUtil.distinctByKey(CommandStructure.Group::name))
                                .map(group -> ApplicationCommandOptionData.builder()
                                    .type(ApplicationCommandOption.Type.SUB_COMMAND_GROUP.getValue())
                                    .name(group.name().toLowerCase())
                                    .description(group.description())
                                    .addAllOptions(
                                        this.getSlashCommands()
                                            .stream()
                                            .filter(command -> parent.name().equalsIgnoreCase(command.getStructure().parent().name()))
                                            .filter(command -> group.name().equalsIgnoreCase(command.getStructure().group().name()))
                                            .filter(command -> command.getStructure().guildId() == guildId)
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

    private @NotNull ImmutableApplicationCommandRequest.Builder buildParentCommand(@NotNull CommandStructure.Parent parent) {
        return ApplicationCommandRequest.builder()
            .type(ApplicationCommand.Type.CHAT_INPUT.getValue())
            .name(parent.name())
            .description(parent.description());
    }

    private @NotNull ImmutableApplicationCommandRequest.Builder buildCommand(@NotNull DiscordCommand command) {
        return ApplicationCommandRequest.builder()
            .type(command.getType().getValue())
            .name(command.getStructure().name())
            .description(command.getStructure().description())
            .nsfw(command.getStructure().nsfw())
            .defaultMemberPermissions(String.valueOf(PermissionSet.of(command.getStructure().userPermissions()).getRawValue()))
            .integrationTypes(InstallContext.intValues(command.getStructure().integrations()))
            .contexts(AccessContext.intValues(command.getStructure().contexts()));
    }

    private @NotNull ApplicationCommandOptionData buildSubCommand(@NotNull SlashCommand command) {
        return ApplicationCommandOptionData.builder()
            .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
            .name(command.getStructure().name())
            .description(command.getStructure().description())
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

    public final @NotNull ConcurrentList<DiscordCommand> getCommandsById(long commandId) {
        return this.getLoadedCommands()
            .stream()
            .filter(command -> command.getId() == commandId)
            .collect(Concurrent.toUnmodifiableList());
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
            .doOnNext(commandData -> this.getCommandReferences(commandData.name(), TypeContext.of(commandData.type().toOptional().orElse(-1)))
                .forEach(command -> this.commandIds.put(command.getClass(), commandData.id().asLong()))
            )
            .thenMany(
                Flux.fromIterable(this.getLoadedCommands())
                    .map(DiscordCommand::getStructure)
                    .map(CommandStructure::guildId)
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
                        .doOnNext(commandData -> this.getCommandReferences(commandData.name(), TypeContext.of(commandData.type().toOptional().orElse(-1)))
                            .forEach(command -> this.commandIds.put(command.getClass(), commandData.id().asLong()))
                        )
                    )
            )
            .then();
    }

    public long getApiCommandId(@NotNull Class<? extends DiscordCommand> commandClass) {
        return this.commandIds.get(commandClass);
    }

    private @NotNull ConcurrentList<DiscordCommand> getCommandReferences(@NotNull String name, @NotNull TypeContext type) {
        return this.getLoadedCommands()
            .stream()
            .filter(command -> command.getType() == type)
            .filter(command -> {
                if (command instanceof SlashCommand slashCommand) {
                    if (StringUtil.isNotEmpty(slashCommand.getStructure().parent().name()))
                        return slashCommand.getStructure().parent().name().equalsIgnoreCase(name);
                    else
                        return slashCommand.getStructure().name().equalsIgnoreCase(name);
                } else
                    return command.getStructure().name().equals(name);
            })
            .collect(Concurrent.toUnmodifiableList());
    }

    private <
        A extends ApplicationCommandInteractionEvent,
        C extends CommandContext<A>,
        T extends DiscordCommand<C>
        > @NotNull ConcurrentList<T> retrieveTypedCommands(
        @NotNull Class<T> referenceType,
        @NotNull ConcurrentList<DiscordCommand> commands,
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
                    this.getLog().info("Command '{}' conflicts with '{}'!", commandEntry.getStructure().name(), commandOverlap.get().getStructure().name());
                    return false;
                }

                return true;
            })
            .collect(Concurrent.toUnmodifiableList());
    }

    private @NotNull ConcurrentList<DiscordCommand> validateCommands(@NotNull DiscordBot discordBot, @NotNull ConcurrentSet<Class<? extends DiscordCommand>> commands) {
        return commands.stream()
            .map(commandClass -> Pair.of(
                commandClass,
                getAnnotation(CommandStructure.class, commandClass)
            ))
            .filter(commandLink -> {
                if (commandLink.getRight().isEmpty()) {
                    this.getLog().warn("'{}' is missing the @CommandStructure annotation, ignoring.", commandLink.getLeft().getName());
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
                if (!validCommandPattern.matcher(commandEntry.getRight().getStructure().name()).matches()) {
                    this.getLog().info("The command name of '{}' ('{}') must only contain english characters!", commandEntry.getLeft().getName(), commandEntry.getRight().getStructure().name());
                    return false;
                }

                return true;
            })
            .map(Pair::getRight)
            .collect(Concurrent.toList());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements dev.sbs.api.util.builder.Builder<CommandHandler> {

        private final DiscordBot discordBot;
        private final ConcurrentSet<Class<? extends DiscordCommand>> commands = Concurrent.newSet();

        public Builder withCommands(@NotNull Class<? extends DiscordCommand>... commands) {
            return this.withCommands(Arrays.asList(commands));
        }

        public Builder withCommands(@NotNull Iterable<Class<? extends DiscordCommand>> commands) {
            commands.forEach(this.commands::add);
            return this;
        }

        @Override
        public @NotNull CommandHandler build() {
            return new CommandHandler(
                this.discordBot,
                this.commands
            );
        }

    }

}
