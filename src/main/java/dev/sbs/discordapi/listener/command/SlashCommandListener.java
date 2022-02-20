package dev.sbs.discordapi.listener.command;

import dev.sbs.api.SimplifiedException;
import dev.sbs.api.util.concurrent.Concurrent;
import dev.sbs.api.util.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.Command;
import dev.sbs.discordapi.command.data.Argument;
import dev.sbs.discordapi.command.data.Parameter;
import dev.sbs.discordapi.context.command.CommandContext;
import dev.sbs.discordapi.context.command.slash.SlashCommandContext;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommand;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.command.Interaction;
import discord4j.discordjson.json.ApplicationCommandInteractionOptionData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Function;

public final class SlashCommandListener extends DiscordListener<ChatInputInteractionEvent> {

    private final Function<ChatInputInteractionEvent, Publisher<Interaction>> prefixFunction;

    public SlashCommandListener(DiscordBot discordBot, GatewayDiscordClient gateway) {
        super(discordBot);
        this.prefixFunction = event -> Mono.justOrEmpty(event.getInteraction());

        this.getLog().info("Registering Slash Commands");
        gateway.getRestClient()
            .getApplicationService()
            .bulkOverwriteGuildApplicationCommand(
                this.getDiscordBot().getClientId().asLong(),
                this.getDiscordBot().getMainGuild().getId().asLong(),
                this.getSlashCommands(this.getDiscordBot().getRootCommandRelationship())
            )
            .subscribe();
    }

    @Override
    public Publisher<Void> apply(ChatInputInteractionEvent event) {
        if (event.getInteraction().getUser().getId().asLong() != 154743493464555521L) // TODO: Limit to myself
            return Mono.empty();

        return Flux.defer(() -> this.prefixFunction.apply(event))
            .filter(interaction -> interaction.getApplicationId().equals(this.getDiscordBot().getClientId())) // Validate Bot ID
            .map(interaction -> interaction.getData().data().toOptional())
            .filter(Optional::isPresent)
            .map(Optional::get)
            .flatMap(commandData -> Flux.just(this.getDeepestCommand(commandData))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(relationship -> {
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
                    return SlashCommandContext.of(this.getDiscordBot(), event, relationship, commandAlias, arguments);
                })
            )
            .flatMap(this::applyCommand);
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

    private Mono<Void> applyCommand(SlashCommandContext commandContext) {
        return Flux.fromIterable(this.getCompactedRelationships())
            .onErrorMap(throwable -> SimplifiedException.wrapNative(throwable).build())
            .doOnError(throwable -> this.getDiscordBot().handleUncaughtException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    commandContext,
                    throwable,
                    "Slash Command Exception"
                )
            ))
            .filter(Command.Relationship.class::isInstance)
            .map(Command.Relationship.class::cast)
            .filter(relationship -> relationship.getCommandClass().equals(commandContext.getCommandClass()))
            .map(relationship -> (Function<CommandContext<?>, Mono<Void>>) relationship.getInstance())
            .single(__ -> Mono.empty())
            .flatMap(handler -> handler.apply(commandContext));
    }

    private ConcurrentList<ApplicationCommandRequest> getSlashCommands(Command.RootRelationship rootRelationship) {
        return rootRelationship.getSubCommands()
            .stream()
            .map(relationship -> ApplicationCommandRequest.builder() // Create Command
                .type(ApplicationCommand.Type.CHAT_INPUT.getValue())
                .name(relationship.getCommandInfo().name())
                .description(relationship.getInstance().getDescription())
                .defaultPermission(true)
                // Handle SubCommand Groups
                .addAllOptions(
                    relationship.getSubCommands()
                        .stream()
                        .flatMap(subRelationship -> subRelationship.getInstance().getGroup().stream())
                        .distinct()
                        .map(commandGroup -> ApplicationCommandOptionData.builder()
                            .type(ApplicationCommandOption.Type.SUB_COMMAND_GROUP.getValue())
                            .name(commandGroup.getKey().toLowerCase())
                            .description(commandGroup.getDescription())
                            .required(commandGroup.isRequired())
                            .addAllOptions(
                                relationship.getSubCommands()
                                    .stream()
                                    .filter(subRelationship -> subRelationship.getInstance()
                                        .getGroup()
                                        .isPresent()
                                    )
                                    .filter(subRelationship -> StringUtil.defaultIfEmpty(
                                        subRelationship.getInstance()
                                            .getGroup()
                                            .get()
                                            .getKey()
                                            .toLowerCase(),
                                        "")
                                        .equals(commandGroup.getKey().toLowerCase())
                                    )
                                    .map(this::buildCommand)
                                    .collect(Concurrent.toList())
                            )
                            .build()
                        )
                        .collect(Concurrent.toList())
                )
                // Handle SubCommands
                .addAllOptions(
                    relationship.getSubCommands()
                        .stream()
                        .filter(subRelationship -> subRelationship.getInstance()
                            .getGroup()
                            .isEmpty()
                        )
                        .map(this::buildCommand)
                        .collect(Concurrent.toList())
                )
                // Handle Parameters
                .addAllOptions(
                    relationship.getInstance()
                        .getParameters()
                        .stream()
                        .map(this::buildParameter)
                        .collect(Concurrent.toList())
                )
                .build()
            )
            .collect(Concurrent.toList());
    }

    private ApplicationCommandOptionData buildCommand(Command.Relationship relationship) {
        return ApplicationCommandOptionData.builder()
            .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
            .name(relationship.getCommandInfo().name())
            .description(relationship.getInstance().getDescription())
            .addAllOptions(
                relationship.getInstance()
                    .getParameters()
                    .stream()
                    .map(this::buildParameter).collect(Concurrent.toList())
            )
            .build();
    }

    private ApplicationCommandOptionData buildParameter(Parameter parameter) {
        return ApplicationCommandOptionData.builder()
            .type(parameter.getType().getOptionType().getValue())
            .name(parameter.getName())
            .description(parameter.getDescription())
            .required(parameter.isRequired())
            .build();
    }

}
