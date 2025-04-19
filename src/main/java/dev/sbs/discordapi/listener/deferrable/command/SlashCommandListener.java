package dev.sbs.discordapi.listener.deferrable.command;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.SlashCommand;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.context.deferrable.command.SlashCommandContext;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public final class SlashCommandListener extends DiscordListener<ChatInputInteractionEvent> {

    public SlashCommandListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public Publisher<Void> apply(@NotNull ChatInputInteractionEvent event) {
        return Mono.just(event.getInteraction())
            .filter(interaction -> interaction.getApplicationId().equals(this.getDiscordBot().getClientId())) // Validate Bot ID
            .flatMap(interaction -> Mono.justOrEmpty(interaction.getData().data().toOptional()))
            .flatMapMany(commandData -> Flux.fromIterable(this.getDiscordBot().getCommandHandler().getCommandsById(event.getCommandId().asLong()))
                .cast(SlashCommand.class)
                .filter(command -> command.matchesInteractionData(commandData))
            )
            .single()
            .flatMap(command -> command.apply(SlashCommandContext.of(
                this.getDiscordBot(),
                event,
                command,
                getActualOptionData(command, event.getOptions())
                    .stream()
                    .flatMap(commandOption -> command.getParameters()
                        .stream()
                        .filter(parameter -> parameter.getName().equals(commandOption.getName()))
                        .map(parameter -> new Argument(event.getInteraction(), parameter, commandOption.getValue().orElseThrow()))
                    )
                    .collect(Concurrent.toList())
            )));
    }

    private static @NotNull ConcurrentList<ApplicationCommandInteractionOption> getActualOptionData(@NotNull SlashCommand slashCommand, @NotNull List<ApplicationCommandInteractionOption> commandOptions) {
        ConcurrentList<ApplicationCommandInteractionOption> options = Concurrent.newList(commandOptions);

        // Build Tree
        ConcurrentList<String> commandTree = Concurrent.newList(slashCommand.getStructure().name().toLowerCase());

        if (StringUtil.isNotEmpty(slashCommand.getStructure().group().name()))
            commandTree.add(slashCommand.getStructure().group().name().toLowerCase());

        if (StringUtil.isNotEmpty(slashCommand.getStructure().parent().name()))
            commandTree.add(slashCommand.getStructure().parent().name().toLowerCase());

        // Invert
        commandTree = commandTree.inverse();

        // Remove Parent
        commandTree.removeFirst();

        while (commandTree.notEmpty()) {
            for (ApplicationCommandInteractionOption option : options) {
                if (option.getName().equals(commandTree.get(0))) {
                    commandTree.removeFirst();
                    options = Concurrent.newList(option.getOptions());
                }
            }
        }

        return options;
    }

}
