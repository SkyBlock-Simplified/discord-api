package dev.simplified.discordapi.harness.command;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.Structure;
import dev.simplified.discordapi.context.command.MessageCommandContext;
import dev.simplified.discordapi.exception.DiscordException;
import dev.simplified.discordapi.response.page.Page;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * A right-click message command ({@code inspect}) for the offline harness that replies with a fixed
 * message, exercising the {@code MessageCommandListener} -> {@code DiscordCommand.apply} -> {@code process} pathway.
 */
@Structure(
    name = "inspect",
    description = "Inspects the target message"
)
public class InspectMessageCommand extends DiscordCommand<MessageCommandContext> {

    public InspectMessageCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** {@inheritDoc} */
    @Override
    protected @NotNull Mono<Void> process(@NotNull MessageCommandContext commandContext) throws DiscordException {
        return commandContext.reply(
            commandContext.buildResponse()
                .withTimeToLive(30)
                .withPages(Page.builder().withContent("message command ran").build())
                .build()
        );
    }

}
