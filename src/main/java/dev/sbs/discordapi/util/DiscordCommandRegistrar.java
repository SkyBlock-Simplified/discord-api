package dev.sbs.discordapi.util;

import dev.sbs.api.SimplifiedException;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.concurrent.Concurrent;
import dev.sbs.api.util.concurrent.ConcurrentList;
import dev.sbs.api.util.concurrent.ConcurrentSet;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.Command;
import dev.sbs.discordapi.command.PrefixCommand;
import dev.sbs.discordapi.command.data.CommandInfo;
import dev.sbs.discordapi.command.exception.CommandException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
                        .withMessage("The designated prefix command ''{0}'' has no command annotation!", prefixCommand.getName())
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

    public void validateCommands(ConcurrentSet<Class<? extends Command>> subCommands, Optional<Class<? extends PrefixCommand>> optionalPrefixCommand) {
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
