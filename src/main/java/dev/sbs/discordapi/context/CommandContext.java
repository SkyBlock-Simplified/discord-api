package dev.sbs.discordapi.context;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.parameter.Parameter;
import discord4j.core.event.domain.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CommandContext<T extends Event> extends TypingContext<T> {

    /**
     * Finds the argument for a known {@link Parameter}.
     *
     * @param name The name of the parameter.
     */
    default @Nullable Argument getArgument(@NotNull String name) {
        return this.getArguments().findFirstOrNull(argument -> argument.getParameter().getName(), name);
    }

    @NotNull ConcurrentList<Argument> getArguments();

}
