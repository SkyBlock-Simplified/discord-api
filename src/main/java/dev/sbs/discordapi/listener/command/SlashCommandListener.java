package dev.sbs.discordapi.listener.command;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.ParentCommand;
import dev.sbs.discordapi.command.data.Argument;
import dev.sbs.discordapi.context.interaction.deferrable.application.slash.SlashCommandContext;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandInteractionOptionData;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

public final class SlashCommandListener extends DiscordListener<ChatInputInteractionEvent> {

    public SlashCommandListener(DiscordBot discordBot) {
        super(discordBot);

        this.getLog().info("Registering Slash Commands");
        this.getDiscordBot()
            .getCommandRegistrar()
            .updateSlashCommands()
            .subscribe();
    }

    @Override
    public Publisher<Void> apply(ChatInputInteractionEvent event) {
        return Mono.just(event.getInteraction())
            .filter(interaction -> interaction.getApplicationId().equals(this.getDiscordBot().getClientId())) // Validate Bot ID
            .flatMap(interaction -> Mono.justOrEmpty(interaction.getData().data().toOptional()))
            .flatMap(commandData -> Mono.justOrEmpty(this.getDeepestCommand(commandData))
                .filter(relationship -> !relationship.getCommandClass().isAssignableFrom(ParentCommand.class))
                .flatMap(relationship -> {
                    ConcurrentList<ApplicationCommandInteractionOptionData> parameterData = this.getDeepestOptionData(relationship, commandData);

                    // Build Arguments
                    ConcurrentList<Argument> arguments = relationship.getInstance()
                        .getParameters()
                        .stream()
                        .map(parameter -> parameterData.stream()
                            .filter(optionData -> optionData.name().equals(parameter.getName()))
                            .findFirst()
                            .map(optionData -> new Argument(parameter, this.getArgumentData(optionData)))
                            .orElse(new Argument(parameter, new Argument.Data()))
                        )
                        .collect(Concurrent.toList());

                    // Build Context
                    SlashCommandContext slashCommandContext = SlashCommandContext.of(
                        this.getDiscordBot(),
                        event,
                        relationship,
                        relationship.getCommandInfo().name(),
                        arguments
                    );

                    // Apply Command
                    return relationship.getInstance().apply(slashCommandContext);
                })
            );
    }

    private Argument.Data getArgumentData(ApplicationCommandInteractionOptionData interactionOptionData) {
        return new Argument.Data(
            interactionOptionData.value().toOptional(),
            interactionOptionData.options()
                .toOptional()
                .orElse(Concurrent.newList())
                .stream()
                .filter(optionData -> !optionData.value().isAbsent())
                .map(optionData -> optionData.value().get())
                .collect(Concurrent.toList())
        );
    }

}
