package dev.sbs.discordapi.listener.autocomplete;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.context.autocomplete.AutoCompleteContext;
import dev.sbs.discordapi.context.deferrable.command.SlashCommandContext;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class AutoCompleteListener extends DiscordListener<ChatInputAutoCompleteEvent> {

    public AutoCompleteListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    @SuppressWarnings("all")
    public Publisher<Void> apply(@NotNull ChatInputAutoCompleteEvent event) {
        return Mono.just(event.getInteraction())
            .filter(interaction -> interaction.getApplicationId().equals(this.getDiscordBot().getClientId())) // Validate Bot ID
            .flatMap(interaction -> Mono.justOrEmpty(interaction.getData().data().toOptional()))
            .flatMapMany(commandData -> Flux.fromIterable(this.getDiscordBot().getCommandHandler().getCommandsById(event.getCommandId().asLong()))
                .filter(command -> this.matchesInteractionData(command, commandData))
            )
            .single()
            .map(command -> (DiscordCommand<SlashCommandContext>) command)
            .flatMap(slashCommand -> event.respondWithSuggestions(
                slashCommand.getParameters()
                    .stream()
                    .filter(parameter -> parameter.getName().equals(event.getFocusedOption().getName()))
                    .findFirst()
                    .map(parameter -> parameter.getAutoComplete()
                        .apply(AutoCompleteContext.of(
                            this.getDiscordBot(),
                            event,
                            slashCommand.getStructure(),
                            new Argument(
                                event.getInteraction(),
                                parameter,
                                event.getFocusedOption().getValue().orElseThrow()
                            )
                        ))
                        .stream()
                        .map(entry -> ApplicationCommandOptionChoiceData.builder()
                            .name(entry.getKey())
                            .value(entry.getValue())
                            .build()
                        )
                        .map(ApplicationCommandOptionChoiceData.class::cast)
                        .collect(Concurrent.toList())
                    )
                    .orElse(Concurrent.newList())
            ));
    }

}
