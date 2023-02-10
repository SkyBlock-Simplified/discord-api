package dev.sbs.discordapi.context;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.command.Command;
import dev.sbs.discordapi.command.data.Argument;
import dev.sbs.discordapi.command.data.CommandInfo;
import dev.sbs.discordapi.command.data.Parameter;
import discord4j.core.event.domain.Event;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface CommandContext<T extends Event> extends EventContext<T> {

    /**
     * Finds the argument for a known {@link Parameter}.
     *
     * @param name The name of the parameter.
     */
    default Argument getArgument(@NotNull String name) {
        return this.getArguments().findFirstOrNull(argument -> argument.getParameter().getName(), name);
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
