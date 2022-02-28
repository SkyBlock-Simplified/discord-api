package dev.sbs.discordapi.util;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentSet;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.Command;
import dev.sbs.discordapi.command.PrefixCommand;
import dev.sbs.discordapi.command.data.CommandInfo;
import dev.sbs.discordapi.command.data.Parameter;
import dev.sbs.discordapi.command.exception.CommandException;
import discord4j.core.object.command.ApplicationCommand;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.Optional;

public class DiscordCommandRegistrar extends DiscordObject {

    @Getter private final @NotNull Command.RootRelationship rootCommandRelationship;
    @Getter private final @NotNull Optional<Class<? extends PrefixCommand>> prefixCommand;
    @Getter private final @NotNull ConcurrentSet<Class<? extends Command>> subCommands;

    DiscordCommandRegistrar(@NotNull DiscordBot discordBot, @NotNull Optional<Class<? extends PrefixCommand>> optionalPrefixCommand, @NotNull ConcurrentSet<Class<? extends Command>> subCommands) {
        super(discordBot);

        optionalPrefixCommand.filter(prefixCommand -> !PrefixCommand.class.equals(prefixCommand))
            .ifPresent(prefixCommand -> {
                this.getLog().debug("Validating Prefix Command");

                if (this.getCommandAnnotation(prefixCommand).isEmpty())
                    throw SimplifiedException.of(CommandException.class)
                        .withMessage("Designating a prefix command ''{0}'' requires a CommandInfo annotation!", prefixCommand.getName())
                        .build();
            });

        this.getLog().info("Validating Commands");
        this.validateCommands(subCommands, optionalPrefixCommand);

        this.getLog().info("Registering Commands");
        this.prefixCommand = optionalPrefixCommand;
        this.subCommands = Concurrent.newUnmodifiableSet(subCommands);
        this.rootCommandRelationship = this.getRootRelationshipTree(subCommands, optionalPrefixCommand);
    }

    private Command.RootRelationship getRootRelationshipTree(ConcurrentSet<Class<? extends Command>> subCommands, Optional<Class<? extends PrefixCommand>> optionalPrefixCommand) {
        ConcurrentList<Command.Relationship> subCommandRelationships = Concurrent.newList();

        subCommands.forEach(commandClass -> this.getCommandAnnotation(commandClass)
            .ifPresentOrElse(commandInfo -> {
                if (commandInfo.parent().equals(Command.class))
                    subCommandRelationships.add(this.getRelationshipTree(subCommands, commandInfo, commandClass));
            }, () -> this.getLog().warn("The command class ''{0}'' does not have a CommandInfo annotation and will be ignored.", commandClass.getName()))
        );

        return optionalPrefixCommand.map(prefixCommand -> new Command.RootRelationship(this.getCommandAnnotation(prefixCommand), prefixCommand, subCommandRelationships)).orElse(Command.RootRelationship.DEFAULT);
    }

    private Command.Relationship getRelationshipTree(ConcurrentSet<Class<? extends Command>> subCommands, CommandInfo currentCommandInfo, Class<? extends Command> currentCommandClass) {
        ConcurrentList<Command.Relationship> subCommandRelationships = Concurrent.newList();

        // Find SubCommands
        subCommands.forEach(subCommandClass -> this.getCommandAnnotation(subCommandClass)
            .filter(subCommandInfo -> subCommandInfo.parent().equals(currentCommandClass))
            .stream()
            .filter(subCommandInfo -> subCommandInfo.parent().equals(currentCommandClass))
            .forEach(subCommandInfo -> subCommandRelationships.add(this.getRelationshipTree(subCommands, subCommandInfo, subCommandClass)))
        );

        return new Command.Relationship(currentCommandInfo, currentCommandClass, Reflection.of(currentCommandClass).newInstance(this.getDiscordBot()), subCommandRelationships);
    }

    private void validateCommands(ConcurrentSet<Class<? extends Command>> subCommands, Optional<Class<? extends PrefixCommand>> optionalPrefixCommand) {
        Optional<CommandInfo> optionalPrefixCommandInfo = this.getCommandAnnotation(optionalPrefixCommand.orElse(PrefixCommand.class));

        // Compare Names and Aliases
        subCommands.parallelStream()
            .map(this::getCommandAnnotation)
            .flatMap(Optional::stream)
            .forEach(commandInfo -> {
                // Compare Prefix Command
                optionalPrefixCommandInfo.ifPresent(prefixCommandInfo -> {
                    if (this.doesCommandMatch(commandInfo, prefixCommandInfo))
                        throw SimplifiedException.of(CommandException.class)
                            .withMessage("Command ''{0}'' conflicts with ''{1}''!", commandInfo.name(), prefixCommandInfo.name())
                            .build();
                });

                // Compare SubCommands
                subCommands.parallelStream()
                    .map(this::getCommandAnnotation)
                    .flatMap(Optional::stream)
                    .filter(compareCommand -> !commandInfo.equals(compareCommand))
                    .filter(compareCommand -> commandInfo.parent().equals(compareCommand.parent()))
                    .filter(compareCommand -> this.doesCommandMatch(commandInfo, compareCommand))
                    .findAny()
                    .ifPresent(compareCommand -> {
                        throw SimplifiedException.of(CommandException.class)
                            .withMessage("Command ''{0}'' conflicts with ''{1}''!", commandInfo.name(), compareCommand.name())
                            .build();
                    });
            });
    }

    public final ConcurrentList<ApplicationCommandRequest> getSlashCommands() {
        return this.getRootCommandRelationship()
            .getSubCommands()
            .stream()
            .map(relationship -> ApplicationCommandRequest.builder() // Create Command
                .type(ApplicationCommand.Type.CHAT_INPUT.getValue())
                .name(relationship.getCommandInfo().name())
                .description(relationship.getInstance().getDescription())
                .defaultPermission(true)
                // Handle SubCommand Groups
                .addAllOptions(
                    relationship.getSubCommands()
                        .stream()
                        .flatMap(subRelationship -> subRelationship.getInstance().getGroup().stream())
                        .distinct()
                        .map(commandGroup -> ApplicationCommandOptionData.builder()
                            .type(ApplicationCommandOption.Type.SUB_COMMAND_GROUP.getValue())
                            .name(commandGroup.getGroup().toLowerCase())
                            .description(commandGroup.getDescription())
                            .required(commandGroup.isRequired())
                            .addAllOptions(
                                relationship.getSubCommands()
                                    .stream()
                                    .filter(subRelationship -> subRelationship.getInstance()
                                        .getGroup()
                                        .isPresent()
                                    )
                                    .filter(subRelationship -> StringUtil.defaultIfEmpty(
                                            subRelationship.getInstance()
                                                .getGroup()
                                                .get()
                                                .getGroup()
                                                .toLowerCase(),
                                            "")
                                        .equals(commandGroup.getGroup().toLowerCase())
                                    )
                                    .map(this::buildCommand)
                                    .collect(Concurrent.toList())
                            )
                            .build()
                        )
                        .collect(Concurrent.toList())
                )
                // Handle SubCommands
                .addAllOptions(
                    relationship.getSubCommands()
                        .stream()
                        .filter(subRelationship -> subRelationship.getInstance()
                            .getGroup()
                            .isEmpty()
                        )
                        .map(this::buildCommand)
                        .collect(Concurrent.toList())
                )
                // Handle Parameters
                .addAllOptions(
                    relationship.getInstance()
                        .getParameters()
                        .stream()
                        .map(this::buildParameter)
                        .collect(Concurrent.toList())
                )
                .build()
            )
            .collect(Concurrent.toList());
    }

    public final ApplicationCommandOptionData buildCommand(Command.Relationship relationship) {
        return ApplicationCommandOptionData.builder()
            .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
            .name(relationship.getCommandInfo().name())
            .description(relationship.getInstance().getDescription())
            .addAllOptions(
                relationship.getInstance()
                    .getParameters()
                    .stream()
                    .map(this::buildParameter).collect(Concurrent.toList())
            )
            .build();
    }

    public final ApplicationCommandOptionData buildParameter(Parameter parameter) {
        return ApplicationCommandOptionData.builder()
            .type(parameter.getType().getOptionType().getValue())
            .name(parameter.getName())
            .description(parameter.getDescription())
            .required(parameter.isRequired())
            .build();
    }

    public Mono<Void> updateSlashCommands() {
        return this.getDiscordBot()
            .getGateway()
            .getRestClient()
            .getApplicationService()
            .bulkOverwriteGuildApplicationCommand(
                this.getDiscordBot().getClientId().asLong(),
                this.getDiscordBot().getMainGuild().getId().asLong(),
                this.getSlashCommands()
            )
            .then();
    }

    public static CommandRegistrarBuilder builder(@NotNull DiscordBot discordBot) {
        return new CommandRegistrarBuilder(discordBot);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CommandRegistrarBuilder implements Builder<DiscordCommandRegistrar> {

        private final DiscordBot discordBot;
        private final ConcurrentSet<Class<? extends Command>> subCommands = Concurrent.newSet();
        private Optional<Class<? extends PrefixCommand>> prefixCommand = Optional.empty();

        public CommandRegistrarBuilder addCommand(@NotNull Class<? extends Command> subCommand) {
            this.subCommands.add(subCommand);
            return this;
        }

        public CommandRegistrarBuilder withPrefix(@Nullable Class<? extends PrefixCommand> prefixCommand) {
            this.prefixCommand = Optional.ofNullable(prefixCommand);
            return this;
        }

        public CommandRegistrarBuilder withCommands(@NotNull Iterable<Class<? extends Command>> subCommands) {
            subCommands.forEach(this.subCommands::add);
            return this;
        }

        @Override
        public DiscordCommandRegistrar build() {
            return new DiscordCommandRegistrar(this.discordBot, this.prefixCommand, this.subCommands);
        }

    }

}
