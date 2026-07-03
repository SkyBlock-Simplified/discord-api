package dev.simplified.discordapi.harness.command;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.Structure;
import dev.simplified.discordapi.context.command.SlashCommandContext;
import dev.simplified.discordapi.exception.DiscordException;
import dev.simplified.discordapi.response.page.Page;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * A minimal global {@code /ping} slash command for the offline harness that replies with {@code pong}.
 * Deliberately not developer-only and requires no permissions, so it runs end-to-end without a guild.
 */
@Structure(
    name = "ping",
    description = "Replies with pong"
)
public class PingCommand extends DiscordCommand<SlashCommandContext> {

    public PingCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** {@inheritDoc} */
    @Override
    protected @NotNull Mono<Void> process(@NotNull SlashCommandContext commandContext) throws DiscordException {
        return commandContext.reply(
            commandContext.buildResponse()
                .withTimeToLive(30)
                .withPages(Page.builder().withContent("pong").build())
                .build()
        );
    }

}
