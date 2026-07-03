package dev.simplified.discordapi.harness.command;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.Structure;
import dev.simplified.discordapi.context.command.SlashCommandContext;
import dev.simplified.discordapi.exception.DiscordException;
import dev.simplified.discordapi.response.Response;
import dev.simplified.discordapi.response.page.Page;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * A slash command that replies with a bare {@link Response#builder()} - deliberately NOT
 * {@code context.buildResponse()} - so the bot never touches the response. Before the {@code DiscordBot}
 * decoupling this would throw the [H1] {@code NullPointerException} at build time; now it builds botless and
 * the rendering context injects the emoji resolver, proving the whole reply path works with no bot on the data.
 */
@Structure(
    name = "bare",
    description = "Replies with a bot-less Response.builder()"
)
public class BareResponseCommand extends DiscordCommand<SlashCommandContext> {

    public BareResponseCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** {@inheritDoc} */
    @Override
    protected @NotNull Mono<Void> process(@NotNull SlashCommandContext commandContext) throws DiscordException {
        return commandContext.reply(
            Response.builder()
                .withTimeToLive(30)
                .withPages(Page.builder().withContent("bare ok").build())
                .build()
        );
    }

}
