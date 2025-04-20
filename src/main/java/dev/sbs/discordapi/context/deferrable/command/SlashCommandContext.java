package dev.sbs.discordapi.context.deferrable.command;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.Structure;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.parameter.Parameter;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

public interface SlashCommandContext extends CommandContext<ChatInputInteractionEvent> {

    /**
     * Finds the argument for a known {@link Parameter}.
     *
     * @param name The name of the parameter.
     */
    default @NotNull Optional<Argument> getArgument(@NotNull String name) {
        return this.getArguments().findFirst(argument -> argument.getParameter().getName(), name);
    }

    @NotNull ConcurrentList<Argument> getArguments();

    @Override
    @NotNull Structure getStructure();

    static @NotNull SlashCommandContext of(@NotNull DiscordBot discordBot, @NotNull ChatInputInteractionEvent event, @NotNull Structure structure, @NotNull ConcurrentList<Argument> arguments) {
        return new Impl(discordBot, event, structure, arguments);
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements SlashCommandContext {

        private final @NotNull DiscordBot discordBot;
        private final @NotNull ChatInputInteractionEvent event;
        private final @NotNull UUID responseId = UUID.randomUUID();
        private final @NotNull Structure structure;
        private final @NotNull ConcurrentList<Argument> arguments;

    }

}