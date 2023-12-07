package dev.sbs.discordapi.context.interaction;

import dev.sbs.discordapi.command.reference.CommandReference;
import dev.sbs.discordapi.context.EventContext;
import discord4j.core.event.domain.Event;
import org.jetbrains.annotations.NotNull;

public interface TypingContext<T extends Event> extends EventContext<T> {

    @NotNull CommandReference<?> getCommand();

    @NotNull CommandReference.Type getType();

}
