package dev.sbs.discordapi.context;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.command.reference.CommandReference;
import discord4j.core.event.domain.Event;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CommandContext<T extends Event> extends EventContext<T> {

    /**
     * Finds the argument for a known {@link Parameter}.
     *
     * @param name The name of the parameter.
     */
    default @Nullable Argument getArgument(@NotNull String name) {
        return this.getArguments().findFirstOrNull(argument -> argument.getParameter().getName(), name);
    }

    @NotNull ConcurrentList<Argument> getArguments();

    @NotNull CommandReference getCommand();

    @NotNull Type getType();

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    enum Type {

        UNKNOWN(-1),
        CHAT_INPUT(1),
        USER(2),
        MESSAGE(3);

        /**
         * The underlying value as represented by Discord.
         */
        @Getter private final int value;

        /**
         * Gets the type of application command. It is guaranteed that invoking {@link #getValue()} from the
         * returned enum will equal ({@code ==}) the supplied {@code value}.
         *
         * @param value The underlying value as represented by Discord.
         * @return The type of command.
         */
        public static Type of(final int value) {
            return switch (value) {
                case 1 -> CHAT_INPUT;
                case 2 -> USER;
                case 3 -> MESSAGE;
                default -> UNKNOWN;
            };
        }

    }

}
