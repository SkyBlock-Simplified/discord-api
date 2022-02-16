package dev.sbs.discordapi.listener.command;

import dev.sbs.api.SimplifiedException;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.concurrent.Concurrent;
import dev.sbs.api.util.concurrent.ConcurrentList;
import dev.sbs.api.util.concurrent.ConcurrentSet;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.Command;
import dev.sbs.discordapi.command.PrefixCommand;
import dev.sbs.discordapi.command.data.Argument;
import dev.sbs.discordapi.command.data.CommandInfo;
import dev.sbs.discordapi.command.data.Parameter;
import dev.sbs.discordapi.command.exception.CommandException;
import dev.sbs.discordapi.context.command.CommandContext;
import dev.sbs.discordapi.context.command.message.MessageCommandContext;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Function;

public class MessageCommandListener extends DiscordListener<MessageCreateEvent> {

    private final Function<MessageCreateEvent, Publisher<Class<? extends PrefixCommand>>> prefixFunction;
    @Getter private final Command.RootRelationship rootCommandRelationship;

    MessageCommandListener(@NotNull DiscordBot discordBot, Optional<Class<? extends PrefixCommand>> optionalPrefixCommand, @NotNull ConcurrentSet<Class<? extends Command>> subCommands) {
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
        this.prefixFunction = event -> Mono.justOrEmpty(optionalPrefixCommand);
        this.rootCommandRelationship = this.getRootRelationshipTree(subCommands, optionalPrefixCommand);
    }

    @Override
    public Publisher<Void> apply(MessageCreateEvent event) {
        String message = StringUtil.defaultIfEmpty(event.getMessage().getContent(), "");
        String[] messageArguments = message.split(" ");

        return Flux.defer(() -> this.prefixFunction.apply(event))
            .filter(prefixCommand -> !event.getMessage().getAuthor().map(User::isBot).orElse(true)) // Ignore Bots
            .filter(prefixCommand -> ListUtil.notEmpty(messageArguments))
            .map(prefixCommand -> prefixCommand == null || this.getCommandAnnotation(prefixCommand).filter(command -> this.doesCommandMatch(command, messageArguments[0])).isPresent())
            .map(prefixCommand -> this.getDeepestCommand(messageArguments))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(relationship -> {
                ConcurrentList<String> remainingArguments = Concurrent.newList(messageArguments);

                // Trim Parent Commands
                this.getParentCommandList(relationship.getCommandClass())
                    .stream()
                    .map(Command.RelationshipData::getOptionalCommandInfo)
                    .flatMap(Optional::stream)
                    .filter(parentCommandInfo -> this.doesCommandMatch(parentCommandInfo, remainingArguments.get(0)))
                    .forEach(__ -> remainingArguments.remove(0));

                // Store Used Alias
                String commandAlias = remainingArguments.get(0);

                // Trim Command
                if (this.doesCommandMatch(relationship.getCommandInfo(), remainingArguments.get(0)))
                    remainingArguments.remove(0);

                // Build Arguments
                ConcurrentList<Argument> arguments = Concurrent.newList();
                if (ListUtil.notEmpty(relationship.getInstance().getParameters())) {
                    ConcurrentList<Parameter> parameters = relationship.getInstance().getParameters();

                    for (int i = 0; i < parameters.size(); i++) {
                        Parameter parameter = parameters.get(i);

                        if (parameter.isRemainder()) {
                            arguments.add(new Argument(parameter, this.getRemainder(remainingArguments)));
                            remainingArguments.clear();
                            break;
                        }

                        // Parse Argument Data
                        Argument.Data argumentData = new Argument.Data(ListUtil.notEmpty(remainingArguments) ? remainingArguments.remove(0) : null);
                        arguments.add(new Argument(parameter, argumentData));
                    }
                }

                // Handle Remaining Arguments
                if (ListUtil.notEmpty(remainingArguments))
                    arguments.add(new Argument(Parameter.DEFAULT, this.getRemainder(remainingArguments)));

                // Build Context
                return MessageCommandContext.of(this.getDiscordBot(), event, relationship, commandAlias, arguments);
            })
            .flatMap(this::applyCommand);
    }

    private ConcurrentList<Argument.Data> getRemainder(ConcurrentList<String> remainingArguments) {
        return remainingArguments.stream()
            .map(Argument.Data::new)
            .collect(Concurrent.toList());
    }

    private Mono<Void> applyCommand(MessageCommandContext commandContext) {
        return Flux.fromIterable(this.getCompactedRelationships())
            .onErrorMap(throwable -> SimplifiedException.wrapNative(throwable).build())
            .doOnError(throwable -> this.getDiscordBot().handleUncaughtException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    commandContext,
                    throwable,
                    "Message Command Exception"
                )
            ))
            .filter(Command.Relationship.class::isInstance)
            .map(Command.Relationship.class::cast)
            .filter(relationship -> relationship.getCommandClass().equals(commandContext.getCommandClass()))
            .map(relationship -> (Function<CommandContext<?>, Mono<Void>>) relationship.getInstance())
            .single(__ -> Mono.empty())
            .flatMap(handler -> handler.apply(commandContext));
    }

    public static CommandListenerBuilder create(@NotNull DiscordBot discordBot) {
        return new CommandListenerBuilder(discordBot);
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
                    if (this.hasConflicts(commandInfo, prefixCommandInfo))
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
                    .filter(compareCommand -> this.hasConflicts(commandInfo, compareCommand))
                    .findAny()
                    .ifPresent(compareCommand -> {
                        throw SimplifiedException.of(CommandException.class)
                            .withMessage("Command ''{0}'' conflicts with ''{1}''!", commandInfo.name(), compareCommand.name())
                            .build();
                    });
            });
    }

    private boolean hasConflicts(CommandInfo commandInfo1, CommandInfo commandInfo2) {
        boolean conflicts = commandInfo1.name().equalsIgnoreCase(commandInfo2.name());

        // Compare Aliases
        for (String alias : commandInfo1.aliases()) {
            conflicts |= commandInfo2.name().equalsIgnoreCase(alias); // Compare Name

            for (String compareAlias : commandInfo2.aliases())
                conflicts |= compareAlias.equalsIgnoreCase(alias);
        }

        return conflicts;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CommandListenerBuilder implements Builder<MessageCommandListener> {

        private final DiscordBot discordBot;
        private final ConcurrentSet<Class<? extends Command>> subCommands = Concurrent.newSet();
        private Optional<Class<? extends PrefixCommand>> prefixCommand = Optional.empty();

        public CommandListenerBuilder addCommand(@NotNull Class<? extends Command> subCommand) {
            this.subCommands.add(subCommand);
            return this;
        }

        public CommandListenerBuilder withPrefix(@Nullable Class<? extends PrefixCommand> prefixCommand) {
            this.prefixCommand = Optional.ofNullable(prefixCommand);
            return this;
        }

        public CommandListenerBuilder withCommands(@NotNull Iterable<Class<? extends Command>> subCommands) {
            subCommands.forEach(this.subCommands::add);
            return this;
        }

        @Override
        public MessageCommandListener build() {
            return new MessageCommandListener(this.discordBot, this.prefixCommand, this.subCommands);
        }

    }

}