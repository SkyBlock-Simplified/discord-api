package dev.sbs.discordapi.context;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.command.Command;
import dev.sbs.discordapi.command.data.Argument;
import dev.sbs.discordapi.command.data.CommandInfo;
import discord4j.core.event.domain.Event;

import java.util.Optional;

public interface CommandContext<T extends Event> extends EventContext<T> {

    default Optional<Argument> getArgument(String name) {
        return this.getArguments().stream().filter(argument -> argument.getParameter().getName().equalsIgnoreCase(name)).findFirst();
    }

    ConcurrentList<Argument> getArguments();

    default CommandInfo getCommandInfo() {
        return this.getRelationship().getCommandInfo();
    }

    default Class<? extends Command> getCommandClass() {
        return this.getRelationship().getCommandClass();
    }

    default Optional<String> getIdentifier() {
        return Optional.of(this.getRelationship().getInstance().getCommandPath(this.isSlashCommand()));
    }

    Command.Relationship getRelationship();

    boolean isSlashCommand();

}