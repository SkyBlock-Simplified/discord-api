package dev.simplified.discordapi.integration.command;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.Structure;
import dev.simplified.discordapi.context.command.UserCommandContext;
import dev.simplified.discordapi.exception.DiscordException;
import dev.simplified.discordapi.response.page.Page;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * A right-click user command ({@code greet}) for the offline harness that replies with a fixed message,
 * exercising the {@code UserCommandListener} -> {@code DiscordCommand.apply} -> {@code process} pathway.
 */
@Structure(
    name = "greet",
    description = "Greets the target user"
)
public class GreetUserCommand extends DiscordCommand<UserCommandContext> {

    public GreetUserCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** {@inheritDoc} */
    @Override
    protected @NotNull Mono<Void> process(@NotNull UserCommandContext commandContext) throws DiscordException {
        return commandContext.reply(
            commandContext.buildResponse()
                .withTimeToLive(30)
                .withPages(Page.builder().withContent("user command ran").build())
                .build()
        );
    }

}
