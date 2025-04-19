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
import discord4j.discordjson.json.ApplicationCommandInteractionData;
import discord4j.discordjson.json.ApplicationCommandInteractionOptionData;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public abstract class SlashCommand extends DiscordCommand<SlashCommandContext> {

    protected SlashCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    public final boolean matchesInteractionData(@NotNull ApplicationCommandInteractionData commandData) {
        if (commandData.name().isAbsent())
            return false;

        String compareName = commandData.name().get();

        if (StringUtil.isNotEmpty(this.getStructure().parent().name())) {
            if (commandData.options().isAbsent() || commandData.options().get().isEmpty())
                return false;

            List<ApplicationCommandInteractionOptionData> options = commandData.options().get();
            ApplicationCommandInteractionOptionData option = options.get(0);

            if (!compareName.equals(this.getStructure().parent().name()))
                return false;

            if (options.get(0).type() > 2)
                return false;

            if (StringUtil.isNotEmpty(this.getStructure().group().name())) {
                if (!option.name().equals(this.getStructure().group().name()))
                    return false;

                if (option.options().isAbsent() || option.options().get().isEmpty())
                    return false;

                options = option.options().get();
                option = options.get(0);
            }

            compareName = option.name();
        }

        return compareName.equals(this.getStructure().name());
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
                throw new ParameterException(parameter, value, "Value '%s' does not match parameter type for '%s'.", value, parameter.getType().name());

            if (!parameter.isValid(value, commandContext))
                throw new ParameterException(parameter, value, "Value '%s' does not validate against parameter '%s'.", value, parameter.getName());
        }
    }

    /*public Embed createHelpEmbed() {
        String commandPath = this.getCommandPath();
        ConcurrentList<Parameter> parameters = this.getParameters();

        Embed.Builder builder = Embed.builder()
            .withAuthor(
                Author.builder()
                    .withName("Help")
                    .withIconUrl(getEmoji("STATUS_INFO").map(Emoji::getUrl))
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
        }*/

        /*if (this.getExampleArguments().notEmpty()) {
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
        }*/

        /*return builder.build();
    }*/

}
