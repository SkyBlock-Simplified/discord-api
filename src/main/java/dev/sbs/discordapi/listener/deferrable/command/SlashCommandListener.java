package dev.sbs.discordapi.listener.deferrable.command;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.reference.SlashCommandReference;
import dev.sbs.discordapi.context.deferrable.command.SlashCommandContext;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class SlashCommandListener extends DiscordListener<ChatInputInteractionEvent> {

    public SlashCommandListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public Publisher<Void> apply(@NotNull ChatInputInteractionEvent event) {
        return Mono.just(event.getInteraction())
            .filter(interaction -> interaction.getApplicationId().equals(this.getDiscordBot().getClientId())) // Validate Bot ID
            .flatMap(interaction -> Mono.justOrEmpty(interaction.getData().data().toOptional()))
            .flatMapMany(commandData -> Flux.fromIterable(this.getCommandsById(event.getCommandId().asLong()))
                .cast(SlashCommandReference.class)
                .filter(command -> this.doesCommandMatch(command, commandData))
            )
            .single()
            .flatMap(command -> command.apply(SlashCommandContext.of(
                this.getDiscordBot(),
                event,
                command,
                this.getActualOptionData(command, event.getOptions())
                    .stream()
                    .flatMap(commandOption -> command.getParameters()
                        .stream()
                        .filter(parameter -> parameter.getName().equals(commandOption.getName()))
                        .map(parameter -> new Argument(event.getInteraction(), parameter, commandOption.getValue().orElseThrow()))
                    )
                    .collect(Concurrent.toList())
            )));
    }

}
