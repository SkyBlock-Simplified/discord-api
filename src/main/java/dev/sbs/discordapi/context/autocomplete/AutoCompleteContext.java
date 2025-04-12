package dev.sbs.discordapi.context.autocomplete;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.SlashCommand;
import dev.sbs.discordapi.command.context.TypeContext;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.context.InteractionContext;
import dev.sbs.discordapi.context.TypingContext;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface AutoCompleteContext extends InteractionContext<ChatInputAutoCompleteEvent>, TypingContext<ChatInputAutoCompleteEvent> {

    @NotNull Argument getArgument();

    @Override
    @NotNull SlashCommand getCommand();

    @Override
    default @NotNull TypeContext getType() {
        return TypeContext.CHAT_INPUT;
    }

    static @NotNull AutoCompleteContext of(@NotNull DiscordBot discordBot, @NotNull ChatInputAutoCompleteEvent event, @NotNull SlashCommand slashCommand, @NotNull Argument argument) {
        return new Impl(discordBot, event, slashCommand, argument);
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements AutoCompleteContext {

        private final @NotNull DiscordBot discordBot;
        private final @NotNull ChatInputAutoCompleteEvent event;
        private final @NotNull UUID responseId = UUID.randomUUID();
        private final @NotNull SlashCommand command;
        private final @NotNull Argument argument;

    }

}