package dev.sbs.discordapi.listener.command;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.Command;
import dev.sbs.discordapi.command.ParentCommand;
import dev.sbs.discordapi.command.data.Argument;
import dev.sbs.discordapi.context.command.slash.SlashCommandContext;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandInteractionOptionData;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Optional;

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
                    ConcurrentList<ApplicationCommandInteractionOptionData> remainingArguments = Concurrent.newList(commandData.options().toOptional().orElse(Concurrent.newList()));

                    // Trim Parent Commands
                    if (ListUtil.notEmpty(remainingArguments)) {
                        this.getParentCommandList(relationship.getCommandClass())
                            .stream()
                            .map(Command.RelationshipData::getOptionalCommandInfo)
                            .flatMap(Optional::stream)
                            .filter(parentCommandInfo -> this.doesCommandMatch(parentCommandInfo, remainingArguments.get(0).name()))
                            .forEach(__ -> remainingArguments.remove(0));
                    }

                    // Store Used Alias
                    String commandAlias = ListUtil.notEmpty(remainingArguments) ? remainingArguments.get(0).name() : relationship.getCommandInfo().name();

                    // Trim Command
                    if (ListUtil.notEmpty(remainingArguments)) {
                        if (this.doesCommandMatch(relationship.getCommandInfo(), remainingArguments.get(0).name()))
                            remainingArguments.remove(0);
                    }

                    // Build Arguments
                    ConcurrentList<Argument> arguments = relationship.getInstance()
                        .getParameters()
                        .stream()
                        .map(parameter -> remainingArguments.stream()
                            .filter(optionData -> optionData.name().equals(parameter.getName()))
                            .findFirst()
                            .map(optionData -> new Argument(parameter, this.getArgumentData(optionData)))
                            .orElse(new Argument(parameter, new Argument.Data()))
                        )
                        .collect(Concurrent.toList());

                    // Build Context
                    SlashCommandContext slashCommandContext = SlashCommandContext.of(this.getDiscordBot(), event, relationship, commandAlias, arguments);

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
