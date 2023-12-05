package dev.sbs.discordapi.context;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.context.interaction.TypingContext;
import dev.sbs.discordapi.context.interaction.deferrable.DeferrableInteractionContext;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface CommandContext<T extends DeferrableInteractionEvent> extends DeferrableInteractionContext<T>, TypingContext<T> {

    /**
     * Finds the argument for a known {@link Parameter}.
     *
     * @param name The name of the parameter.
     */
    default @NotNull Optional<Argument> getArgument(@NotNull String name) {
        return this.getArguments().findFirst(argument -> argument.getParameter().getName(), name);
    }

    @NotNull ConcurrentList<Argument> getArguments();

}
