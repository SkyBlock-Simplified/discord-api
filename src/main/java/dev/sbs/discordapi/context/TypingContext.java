package dev.sbs.discordapi.context;

import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.command.Structure;
import discord4j.core.event.domain.Event;
import org.jetbrains.annotations.NotNull;

/**
 * Specialization of {@link EventContext} for events that carry command {@link Structure}
 * metadata and a {@link DiscordCommand.Type}, enabling the command system to identify which
 * command definition the event is associated with.
 *
 * <p>
 * Implemented by context types that participate in command routing, such as
 * {@link dev.sbs.discordapi.context.autocomplete.AutoCompleteContext AutoCompleteContext}
 * and the deferrable command contexts.
 *
 * @param <T> the Discord4J {@link Event} type wrapped by this context
 * @see Structure
 * @see DiscordCommand.Type
 */
public interface TypingContext<T extends Event> extends EventContext<T> {

    /**
     * Returns the {@link Structure} annotation metadata for the command associated with
     * this event.
     *
     * @return the command structure
     */
    @NotNull Structure getStructure();

    /**
     * Returns the {@link DiscordCommand.Type} of the command associated with this event.
     *
     * @return the command type
     */
    @NotNull DiscordCommand.Type getType();

}
