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
 * A bare subcommand ({@code /config get <key>}) for the offline harness, nested directly under the
 * {@code config} parent with no group. Exercises parent/subcommand routing plus leaf-option resolution.
 */
@Structure(
    name = "get",
    description = "Reads a config value",
    parent = @Structure.Parent(name = "config", description = "Manage configuration")
)
public class ConfigGetCommand extends DiscordCommand<SlashCommandContext> {

    /** Slash-option identifier for the config key to read. */
    public static final @NotNull String OPTION_KEY = "key";

    public ConfigGetCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull ConcurrentList<Parameter> getParameters() {
        return Concurrent.newUnmodifiableList(
            Parameter.builder()
                .withName(OPTION_KEY)
                .withDescription("The config key to read")
                .withType(Parameter.Type.TEXT)
                .isRequired()
                .build()
        );
    }

    /** {@inheritDoc} */
    @Override
    protected @NotNull Mono<Void> process(@NotNull SlashCommandContext commandContext) throws DiscordException {
        String key = commandContext.getArgument(OPTION_KEY).map(Argument::asString).orElse("<none>");

        return commandContext.reply(
            commandContext.buildResponse()
                .withTimeToLive(30)
                .withPages(Page.builder().withContent("config get key=" + key).build())
                .build()
        );
    }

}
