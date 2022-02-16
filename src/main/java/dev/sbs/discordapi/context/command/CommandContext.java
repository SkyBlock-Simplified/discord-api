package dev.sbs.discordapi.context.command;

import dev.sbs.api.util.concurrent.ConcurrentList;
import dev.sbs.discordapi.command.Command;
import dev.sbs.discordapi.command.data.Argument;
import dev.sbs.discordapi.command.data.CommandInfo;
import dev.sbs.discordapi.context.EventContext;
import discord4j.core.event.domain.Event;

public interface CommandContext<T extends Event> extends EventContext<T> {

    ConcurrentList<Argument> getArguments();

    default CommandInfo getCommandInfo() {
        return this.getRelationship().getCommandInfo();
    }

    default Class<? extends Command> getCommandClass() {
        return this.getRelationship().getCommandClass();
    }

    Command.Relationship getRelationship();

}
