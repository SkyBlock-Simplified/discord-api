package dev.sbs.discordapi.context;

import dev.sbs.discordapi.command.Structure;
import dev.sbs.discordapi.command.context.TypeContext;
import discord4j.core.event.domain.Event;
import org.jetbrains.annotations.NotNull;

public interface TypingContext<T extends Event> extends EventContext<T> {

    @NotNull Structure getStructure();

    @NotNull TypeContext getType();

}
