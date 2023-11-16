package dev.sbs.discordapi.listener;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.reference.SlashCommandReference;
import dev.sbs.discordapi.context.interaction.autocomplete.AutoCompleteContext;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

public final class AutoCompleteListener extends DiscordListener<ChatInputAutoCompleteEvent> {

    public AutoCompleteListener(DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public Publisher<Void> apply(ChatInputAutoCompleteEvent event) {
        return Mono.just(event.getInteraction())
            .filter(interaction -> interaction.getApplicationId().equals(this.getDiscordBot().getClientId())) // Validate Bot ID
            .flatMap(interaction -> Mono.justOrEmpty(interaction.getData().data().toOptional()))
            .flatMap(commandData -> Mono.justOrEmpty(this.getCommandById(event.getCommandId().asLong())))
            .cast(SlashCommandReference.class)
            .flatMap(slashCommand -> {
                // Build Argument
                Argument argument = slashCommand.getParameters()
                    .stream()
                    .filter(parameter -> parameter.getName().equals(event.getFocusedOption().getName()))
                    .map(parameter -> new Argument(event.getInteraction(), parameter, event.getFocusedOption().getValue().orElseThrow()))
                    .findFirst()
                    .orElseThrow();

                // Build Context
                AutoCompleteContext autoCompleteContext = AutoCompleteContext.of(
                    this.getDiscordBot(),
                    event,
                    slashCommand,
                    argument
                );

                // Build Choices
                ConcurrentList<ApplicationCommandOptionChoiceData> choices = argument.getParameter()
                    .getAutoComplete()
                    .apply(autoCompleteContext)
                    .stream()
                    .map(entry -> ApplicationCommandOptionChoiceData.builder()
                        .name(entry.getKey())
                        .value(entry.getValue())
                        .build()
                    )
                    .collect(Concurrent.toList());

                // Apply AutoComplete
                return event.respondWithSuggestions(choices);
            });
    }

}
