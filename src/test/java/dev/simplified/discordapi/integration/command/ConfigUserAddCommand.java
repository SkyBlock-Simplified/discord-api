package dev.simplified.discordapi.integration.command;

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
 * A grouped subcommand ({@code /config user add <name>}) for the offline harness with a fully resolved
 * {@code @Structure} - parent {@code config}, group {@code user}, and name {@code add} all populated.
 * Exercises the deepest slash-command nesting: parent -> subcommand group -> subcommand -> leaf option.
 */
@Structure(
    name = "add",
    description = "Adds a config user",
    parent = @Structure.Parent(name = "config", description = "Manage configuration"),
    group = @Structure.Group(name = "user", description = "Manage config users")
)
public class ConfigUserAddCommand extends DiscordCommand<SlashCommandContext> {

    /** Slash-option identifier for the user name to add. */
    public static final @NotNull String OPTION_NAME = "name";

    public ConfigUserAddCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull ConcurrentList<Parameter> getParameters() {
        return Concurrent.newUnmodifiableList(
            Parameter.builder()
                .withName(OPTION_NAME)
                .withDescription("The user name to add")
                .withType(Parameter.Type.TEXT)
                .isRequired()
                .build()
        );
    }

    /** {@inheritDoc} */
    @Override
    protected @NotNull Mono<Void> process(@NotNull SlashCommandContext commandContext) throws DiscordException {
        String name = commandContext.getArgument(OPTION_NAME).map(Argument::asString).orElse("<none>");

        return commandContext.reply(
            commandContext.buildResponse()
                .withTimeToLive(30)
                .withPages(Page.builder().withContent("config user add name=" + name).build())
                .build()
        );
    }

}
