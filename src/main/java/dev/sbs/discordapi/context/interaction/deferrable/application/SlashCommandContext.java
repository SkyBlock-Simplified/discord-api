package dev.sbs.discordapi.context.interaction.deferrable.application;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.command.reference.SlashCommandReference;
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
    @NotNull SlashCommandReference getCommand();

    static @NotNull SlashCommandContext of(@NotNull DiscordBot discordBot, @NotNull ChatInputInteractionEvent event, @NotNull SlashCommandReference command, @NotNull ConcurrentList<Argument> arguments) {
        return new Impl(discordBot, event, command, arguments);
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements SlashCommandContext {

        private final @NotNull DiscordBot discordBot;
        private final @NotNull ChatInputInteractionEvent event;
        private final @NotNull UUID responseId = UUID.randomUUID();
        private final @NotNull SlashCommandReference command;
        private final @NotNull ConcurrentList<Argument> arguments;

    }

}