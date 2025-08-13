package dev.sbs.discordapi.listener.deferrable.command;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.context.deferrable.command.SlashCommandContext;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

public final class SlashCommandListener extends DiscordListener<ChatInputInteractionEvent> {

    public SlashCommandListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    @SuppressWarnings("all")
    public Publisher<Void> apply(@NotNull ChatInputInteractionEvent event) {
        return Mono.just(event.getInteraction())
            .flatMap(interaction -> Mono.justOrEmpty(interaction.getData().data().toOptional()))
            .flatMapMany(commandData -> Flux.fromIterable(this.getDiscordBot().getCommandHandler().getCommandsById(event.getCommandId().asLong()))
                .filter(command -> this.matchesInteractionData(command, commandData))
            )
            .single()
            .map(command -> (DiscordCommand<SlashCommandContext>) command)
            .flatMap(command -> command.apply(SlashCommandContext.of(
                this.getDiscordBot(),
                event,
                command.getStructure(),
                this.getActualOptionData(command, event.getOptions())
                    .stream()
                    .flatMap(commandOption -> command.getParameters()
                        .stream()
                        .filter(parameter -> parameter.getName().equals(commandOption.getName()))
                        .map(parameter -> new Argument(event.getInteraction(), parameter, commandOption.getValue().orElseThrow()))
                    )
                    .collect(Concurrent.toList())
            )))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private @NotNull ConcurrentList<ApplicationCommandInteractionOption> getActualOptionData(@NotNull DiscordCommand<?> command, @NotNull List<ApplicationCommandInteractionOption> commandOptions) {
        ConcurrentList<ApplicationCommandInteractionOption> options = Concurrent.newList(commandOptions);

        // Build Tree
        ConcurrentList<String> commandTree = Concurrent.newList(command.getStructure().name().toLowerCase());

        if (StringUtil.isNotEmpty(command.getStructure().group().name()))
            commandTree.add(command.getStructure().group().name().toLowerCase());

        if (StringUtil.isNotEmpty(command.getStructure().parent().name()))
            commandTree.add(command.getStructure().parent().name().toLowerCase());

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
