package dev.sbs.discordapi.command.impl;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.unmodifiable.ConcurrentUnmodifiableList;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.exception.parameter.InvalidParameterException;
import dev.sbs.discordapi.command.exception.parameter.MissingParameterException;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.command.reference.SlashCommandReference;
import dev.sbs.discordapi.context.interaction.deferrable.application.SlashCommandContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.embed.structure.Author;
import dev.sbs.discordapi.response.embed.structure.Footer;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.Instant;
import java.util.Optional;

@Getter
public abstract class SlashCommand extends DiscordCommand<ChatInputInteractionEvent, SlashCommandContext> implements SlashCommandReference {

    private @NotNull Optional<Emoji> emoji = Optional.empty();

    protected SlashCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    public @NotNull ConcurrentUnmodifiableList<String> getExampleArguments() {
        return NO_EXAMPLES;
    }

    @Override
    protected void handleAdditionalChecks(@NotNull SlashCommandContext commandContext) {
        // Validate Arguments
        for (Parameter parameter : this.getParameters()) {
            Optional<Argument> argument = commandContext.getArgument(parameter.getName());

            if (argument.isEmpty()) {
                if (parameter.isRequired())
                    throw SimplifiedException.of(MissingParameterException.class)
                        .addData("PARAMETER", parameter)
                        .addData("MISSING", true)
                        .build();
            } else {
                String value = argument.map(Argument::asString).get();

                if (!parameter.getType().isValid(value))
                    throw SimplifiedException.of(InvalidParameterException.class)
                        .addData("PARAMETER", parameter)
                        .addData("VALUE", value)
                        .addData("MISSING", false)
                        .build();

                if (!parameter.isValid(value, commandContext))
                    throw SimplifiedException.of(InvalidParameterException.class)
                        .addData("PARAMETER", parameter)
                        .addData("VALUE", value)
                        .addData("MISSING", false)
                        .build();
            }
        }
    }

    public Embed createHelpEmbed() {
        String commandPath = this.getCommandPath();
        ConcurrentList<Parameter> parameters = this.getParameters();

        Embed.Builder builder = Embed.builder()
            .withAuthor(
                Author.builder()
                    .withName("Help")
                    .withIconUrl(getEmoji("STATUS_INFO").map(Emoji::getUrl))
                    .build()
            )
            .withTitle("Command :: %s", this.getName())
            .withDescription(this.getLongDescription())
            .withFooter(
                Footer.builder()
                    .withTimestamp(Instant.now())
                    .build()
            )
            .withColor(Color.DARK_GRAY);

        if (ListUtil.notEmpty(parameters)) {
            builder.withField(
                "Usage",
                String.format(
                    """
                        <> - Required Parameters
                        [] - Optional Parameters
    
                        %s %s""",
                    commandPath,
                    StringUtil.join(
                        parameters.stream()
                            .map(parameter -> parameter.isRequired() ? String.format("<%s>", parameter.getName()) : String.format("[%s]", parameter.getName()))
                            .collect(Concurrent.toList()),
                        " "
                    )
                )
            );
        }

        if (ListUtil.notEmpty(this.getExampleArguments())) {
            builder.withField(
                "Examples",
                StringUtil.join(
                    this.getExampleArguments()
                        .stream()
                        .map(example -> String.format("%s %s", commandPath, example))
                        .collect(Concurrent.toList()),
                    "\n"
                )
            );
        }

        return builder.build();
    }

}
