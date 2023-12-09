package dev.sbs.discordapi.context;

import dev.sbs.discordapi.command.reference.CommandReference;
import discord4j.core.event.domain.Event;
import org.jetbrains.annotations.NotNull;

public interface TypingContext<T extends Event> extends EventContext<T> {

    @NotNull CommandReference<?> getCommand();

    @NotNull CommandReference.Type getType();

}
