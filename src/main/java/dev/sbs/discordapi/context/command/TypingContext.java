package dev.sbs.discordapi.context.command;

import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.command.Structure;
import dev.sbs.discordapi.context.EventContext;
import discord4j.core.event.domain.Event;
import org.jetbrains.annotations.NotNull;

/**
 * Specialization of {@link EventContext} for events that carry command {@link Structure}
 * metadata and a {@link DiscordCommand.Type}, enabling the command system to identify which
 * command definition the event is associated with.
 *
 * <p>
 * Implemented by context types that participate in command routing, such as
 * {@link AutoCompleteContext AutoCompleteContext}
 * and the deferrable command contexts.
 *
 * @param <T> the Discord4J {@link Event} type wrapped by this context
 * @see Structure
 * @see DiscordCommand.Type
 */
public interface TypingContext<T extends Event> extends EventContext<T> {

    /** The {@link Structure} annotation metadata for the command associated with this event. */
    @NotNull Structure getStructure();

    /** The {@link DiscordCommand.Type} of the command associated with this event. */
    @NotNull DiscordCommand.Type getType();

}
