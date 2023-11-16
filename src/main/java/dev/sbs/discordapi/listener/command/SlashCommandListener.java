package dev.sbs.discordapi.listener.command;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.reference.SlashCommandReference;
import dev.sbs.discordapi.context.interaction.deferrable.application.slash.SlashCommandContext;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

public final class SlashCommandListener extends DiscordListener<ChatInputInteractionEvent> {

    public SlashCommandListener(DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public Publisher<Void> apply(ChatInputInteractionEvent event) {
        return Mono.just(event.getInteraction())
            .filter(interaction -> interaction.getApplicationId().equals(this.getDiscordBot().getClientId())) // Validate Bot ID
            .flatMap(interaction -> Mono.justOrEmpty(interaction.getData().data().toOptional()))
            .flatMap(commandData -> Mono.justOrEmpty(this.getCommandById(event.getCommandId().asLong())))
            .cast(SlashCommandReference.class)
            .flatMap(slashCommand -> {
                // Build Arguments
                ConcurrentList<Argument> arguments = this.getCommandOptionData(
                        slashCommand,
                        event.getOptions()
                    )
                    .stream()
                    .flatMap(commandOption -> slashCommand.getParameters()
                                 .stream()
                        .filter(parameter -> parameter.getName().equals(commandOption.getName()))
                        .map(parameter -> new Argument(event.getInteraction(), parameter, commandOption.getValue().orElseThrow()))
                    )
                    .collect(Concurrent.toList());

                // Build Context
                SlashCommandContext slashCommandContext = SlashCommandContext.of(
                    this.getDiscordBot(),
                    event,
                    slashCommand,
                    arguments
                );

                // Apply Command
                return this.getDiscordBot()
                    .getCommandRegistrar()
                    .getSlashCommands()
                    .get(slashCommand.getClass())
                    .apply(slashCommandContext);
            });
    }

}
