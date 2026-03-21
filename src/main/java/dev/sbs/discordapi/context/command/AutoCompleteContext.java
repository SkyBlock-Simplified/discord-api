package dev.sbs.discordapi.context.command;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.command.Structure;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.context.InteractionContext;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Specialization of {@link InteractionContext} and {@link TypingContext} for Discord
 * autocomplete events, providing access to the focused {@link Argument} and the
 * associated command {@link Structure}.
 *
 * <p>
 * Autocomplete contexts are always of type {@link DiscordCommand.Type#CHAT_INPUT} and
 * wrap a {@link ChatInputAutoCompleteEvent} from Discord4J.
 *
 * @see InteractionContext
 * @see TypingContext
 * @see Argument
 */
public interface AutoCompleteContext extends InteractionContext<ChatInputAutoCompleteEvent>, TypingContext<ChatInputAutoCompleteEvent> {

    /**
     * Returns the {@link Argument} that is currently being autocompleted by the user.
     *
     * @return the focused argument
     */
    @NotNull Argument getArgument();

    /** {@inheritDoc} */
    @Override
    @NotNull Structure getStructure();

    /** {@inheritDoc} */
    @Override
    default @NotNull DiscordCommand.Type getType() {
        return DiscordCommand.Type.CHAT_INPUT;
    }

    /**
     * Creates a new {@link AutoCompleteContext} from the given event and metadata.
     *
     * @param discordBot the bot instance
     * @param event the autocomplete event
     * @param structure the command structure annotation for the matching command
     * @param argument the focused argument being autocompleted
     * @return a new autocomplete context
     */
    static @NotNull AutoCompleteContext of(@NotNull DiscordBot discordBot, @NotNull ChatInputAutoCompleteEvent event, @NotNull Structure structure, @NotNull Argument argument) {
        return new Impl(discordBot, event, structure, argument);
    }

    /**
     * Default implementation of {@link AutoCompleteContext} that stores the bot instance,
     * autocomplete event, command structure, and focused argument.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements AutoCompleteContext {

        /**
         * The bot instance that received this event.
         */
        private final @NotNull DiscordBot discordBot;

        /**
         * The underlying autocomplete event.
         */
        private final @NotNull ChatInputAutoCompleteEvent event;

        /**
         * A randomly generated response identifier for this autocomplete context.
         */
        private final @NotNull UUID responseId = UUID.randomUUID();

        /**
         * The command structure annotation for the matching command.
         */
        private final @NotNull Structure structure;

        /**
         * The focused argument being autocompleted.
         */
        private final @NotNull Argument argument;

    }

}