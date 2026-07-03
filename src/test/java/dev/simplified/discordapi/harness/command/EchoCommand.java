package dev.simplified.discordapi.harness.command;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.Structure;
import dev.simplified.discordapi.command.parameter.Argument;
import dev.simplified.discordapi.command.parameter.Parameter;
import dev.simplified.discordapi.context.command.SlashCommandContext;
import dev.simplified.discordapi.exception.DiscordException;
import dev.simplified.discordapi.response.page.Page;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * A flat global {@code /echo <text>} slash command for the offline harness that replies with the resolved
 * value of its top-level {@code text} option, exercising slash-command option resolution end-to-end.
 */
@Structure(
    name = "echo",
    description = "Echoes the supplied text"
)
public class EchoCommand extends DiscordCommand<SlashCommandContext> {

    /** Slash-option identifier for the text to echo. */
    public static final @NotNull String OPTION_TEXT = "text";

    public EchoCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull ConcurrentList<Parameter> getParameters() {
        return Concurrent.newUnmodifiableList(
            Parameter.builder()
                .withName(OPTION_TEXT)
                .withDescription("The text to echo back")
                .withType(Parameter.Type.TEXT)
                .isRequired()
                .build()
        );
    }

    /** {@inheritDoc} */
    @Override
    protected @NotNull Mono<Void> process(@NotNull SlashCommandContext commandContext) throws DiscordException {
        String text = commandContext.getArgument(OPTION_TEXT).map(Argument::asString).orElse("<none>");

        return commandContext.reply(
            commandContext.buildResponse()
                .withTimeToLive(30)
                .withPages(Page.builder().withContent("echo " + text).build())
                .build()
        );
    }

}
