package dev.sbs.discordapi.command;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.collection.concurrent.unmodifiable.ConcurrentUnmodifiableList;
import dev.sbs.api.util.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.context.TypeContext;
import dev.sbs.discordapi.command.exception.input.ParameterException;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.context.deferrable.command.SlashCommandContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.embed.structure.Author;
import dev.sbs.discordapi.response.embed.structure.Footer;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.Instant;
import java.util.Optional;

public abstract class SlashCommand extends DiscordCommand<SlashCommandContext> {

    protected SlashCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    public @NotNull ConcurrentUnmodifiableList<String> getExampleArguments() {
        return NO_EXAMPLES;
    }

    public final @NotNull ConcurrentList<String> getCommandTree() {
        ConcurrentList<String> commandTree = Concurrent.newList(this.getStructure().name().toLowerCase());

        if (StringUtil.isNotEmpty(this.getStructure().group()))
            commandTree.add(this.getStructure().group().toLowerCase());

        if (StringUtil.isNotEmpty(this.getStructure().parent()))
            commandTree.add(this.getStructure().parent().toLowerCase());

        return commandTree.inverse().toUnmodifiableList();
    }

    public final @NotNull String getCommandPath() {
        return String.format(
            "/%s",
            StringUtil.join(this.getCommandTree(), " ")
        );
    }

    public final @NotNull Optional<Parameter> getParameter(int index) {
        ConcurrentList<Parameter> parameters = this.getParameters();
        index = Math.max(0, index);
        return Optional.ofNullable(index < parameters.size() ? parameters.get(index) : null);
    }

    public @NotNull ConcurrentUnmodifiableList<Parameter> getParameters() {
        return Concurrent.newUnmodifiableList();
    }

    @Override
    public final @NotNull TypeContext getType() {
        return TypeContext.CHAT_INPUT;
    }

    @Override
    protected void handleAdditionalChecks(@NotNull SlashCommandContext commandContext) {
        // Validate Arguments
        for (Parameter parameter : this.getParameters()) {
            Optional<Argument> argument = commandContext.getArgument(parameter.getName());

            if (argument.isEmpty())
                continue;

            String value = argument.map(Argument::asString).get();

            if (!parameter.getType().isValid(value))
                throw new ParameterException(parameter, value, "Type of '%s' does not match '%s'.", value, parameter.getType().name());

            if (!parameter.isValid(value, commandContext))
                throw new ParameterException(parameter, value, "Value '%s' does not validate against '%s'.", value, parameter.getName());
        }
    }

    public Embed createHelpEmbed() {
        String commandPath = this.getCommandPath();
        ConcurrentList<Parameter> parameters = this.getParameters();

        Embed.Builder builder = Embed.builder()
            .withAuthor(
                Author.builder()
                    .withName("Help")
                    .withIconUrl(this.getDiscordBot().getEmojiHandler().getEmoji("STATUS_INFO").map(Emoji::getUrl))
                    .build()
            )
            .withTitle("Command :: %s", this.getStructure().name())
            .withDescription(this.getStructure().description())
            .withFooter(
                Footer.builder()
                    .withTimestamp(Instant.now())
                    .build()
            )
            .withColor(Color.DARK_GRAY);

        if (parameters.notEmpty()) {
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

        if (this.getExampleArguments().notEmpty()) {
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
