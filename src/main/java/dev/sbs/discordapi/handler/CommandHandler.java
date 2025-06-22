package dev.sbs.discordapi.handler;

import dev.sbs.api.builder.ClassBuilder;
import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.collection.concurrent.ConcurrentMap;
import dev.sbs.api.collection.concurrent.ConcurrentSet;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.stream.StreamUtil;
import dev.sbs.api.stream.pair.Pair;
import dev.sbs.api.util.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.command.Structure;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.context.deferrable.command.CommandContext;
import dev.sbs.discordapi.context.deferrable.command.MessageCommandContext;
import dev.sbs.discordapi.context.deferrable.command.SlashCommandContext;
import dev.sbs.discordapi.context.deferrable.command.UserCommandContext;
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
public final class CommandHandler extends DiscordReference {

    private static final Pattern validCommandPattern = Pattern.compile("^[\\w-]{1,32}$");
    private final @NotNull ConcurrentMap<Class<? extends DiscordCommand>, Long> commandIds = Concurrent.newMap();
    @Getter private final @NotNull ConcurrentList<DiscordCommand> loadedCommands;
    @Getter private final @NotNull ConcurrentList<DiscordCommand<SlashCommandContext>> slashCommands;
    @Getter private final @NotNull ConcurrentList<DiscordCommand<UserCommandContext>> userCommands;
    @Getter private final @NotNull ConcurrentList<DiscordCommand<MessageCommandContext>> messageCommands;

    CommandHandler(
        @NotNull DiscordBot discordBot,
        @NotNull ConcurrentSet<Class<DiscordCommand>> commands
    ) {
        super(discordBot);

        this.getLog().info("Validating Commands");
        this.loadedCommands = this.validateCommands(discordBot, commands);

        this.getLog().info("Filtering Commands");
        this.slashCommands = this.retrieveTypedCommands(
            SlashCommandContext.class,
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
            UserCommandContext.class,
            this.loadedCommands,
            (commandEntry, compareEntry) -> commandEntry.getStructure().name().equalsIgnoreCase(compareEntry.getStructure().name())
        );

        this.messageCommands = this.retrieveTypedCommands(
            MessageCommandContext.class,
            this.loadedCommands,
            (commandEntry, compareEntry) -> commandEntry.getStructure().name().equalsIgnoreCase(compareEntry.getStructure().name())
        );
    }

    public static @NotNull Builder builder(@NotNull DiscordBot discordBot) {
        return new Builder(discordBot);
    }

    private @NotNull ConcurrentList<ApplicationCommandRequest> buildCommandRequests(long guildId) {
        return Stream.concat(
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

    private @NotNull ImmutableApplicationCommandRequest.Builder buildParentCommand(@NotNull Structure.Parent parent) {
        return ApplicationCommandRequest.builder()
            .type(ApplicationCommand.Type.CHAT_INPUT.getValue())
            .name(parent.name())
            .description(parent.description());
    }

    private @NotNull ImmutableApplicationCommandRequest.Builder buildCommand(@NotNull DiscordCommand command) {
        return ApplicationCommandRequest.builder()
            .type(command.getStructure().type().getValue())
            .name(command.getStructure().name())
            .description(command.getStructure().description())
            .nsfw(command.getStructure().nsfw())
            .defaultMemberPermissions(String.valueOf(PermissionSet.of(command.getStructure().userPermissions()).getRawValue()))
            .integrationTypes(DiscordCommand.Install.intValues(command.getStructure().integrations()))
            .contexts(DiscordCommand.Access.intValues(command.getStructure().contexts()));
    }

    private @NotNull ApplicationCommandOptionData buildSubCommand(@NotNull DiscordCommand<SlashCommandContext> command) {
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

    public @NotNull ConcurrentList<DiscordCommand> getCommandsById(long commandId) {
        return this.getLoadedCommands()
            .stream()
            .filter(command -> command.getId() == commandId)
            .collect(Concurrent.toUnmodifiableList());
    }

    public Long getCommandId(@NotNull Class<? extends DiscordCommand> commandClass) {
        return this.commandIds.get(commandClass);
    }

    private @NotNull ConcurrentList<DiscordCommand> getCommandReferences(@NotNull String name, @NotNull DiscordCommand.Type type) {
        return this.getLoadedCommands()
            .stream()
            .filter(command -> command.getStructure().type() == type)
            .filter(command -> {
                if (command.getStructure().type() == DiscordCommand.Type.CHAT_INPUT) {
                    if (StringUtil.isNotEmpty(command.getStructure().parent().name()))
                        return command.getStructure().parent().name().equalsIgnoreCase(name);
                    else
                        return command.getStructure().name().equalsIgnoreCase(name);
                } else
                    return command.getStructure().name().equals(name);
            })
            .collect(Concurrent.toUnmodifiableList());
    }

    @SuppressWarnings("all")
    private <A extends ApplicationCommandInteractionEvent, C extends CommandContext<A>> @NotNull ConcurrentList<DiscordCommand<C>> retrieveTypedCommands(
        @NotNull Class<C> type,
        @NotNull ConcurrentList<DiscordCommand> commands,
        @NotNull BiPredicate<DiscordCommand, DiscordCommand> filter
    ) {
        return commands.stream()
            .filter(command -> type.isAssignableFrom(command.getContextType()))
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

    public @NotNull Mono<Void> updateApplicationCommands() {
        return this.updateGlobalApplicationCommands()
            .then(this.updateGuildApplicationCommands());
    }

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

    private @NotNull ConcurrentList<DiscordCommand> validateCommands(@NotNull DiscordBot discordBot, @NotNull ConcurrentSet<Class<DiscordCommand>> commands) {
        return commands.stream()
            .map(commandClass -> Pair.of(
                commandClass,
                getAnnotation(Structure.class, commandClass)
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
    public static class Builder implements ClassBuilder<CommandHandler> {

        private final DiscordBot discordBot;
        private final ConcurrentSet<Class<DiscordCommand>> commands = Concurrent.newSet();

        public Builder withCommands(@NotNull Class<DiscordCommand>... commands) {
            return this.withCommands(Arrays.asList(commands));
        }

        public Builder withCommands(@NotNull Iterable<Class<DiscordCommand>> commands) {
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
