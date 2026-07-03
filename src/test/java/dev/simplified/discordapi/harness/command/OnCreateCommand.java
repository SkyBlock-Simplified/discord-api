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
 * A global {@code /oncreate} slash command whose reply registers an {@code onCreate} handler that edits
 * the message content once its {@code MESSAGE_CREATE} arrives - exercising the {@code MessageCreateListener}
 * -> cached-response lookup -> {@code onCreate} pathway.
 */
@Structure(
    name = "oncreate",
    description = "Replies with a response that reacts to its own message-create"
)
public class OnCreateCommand extends DiscordCommand<SlashCommandContext> {

    public OnCreateCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** {@inheritDoc} */
    @Override
    protected @NotNull Mono<Void> process(@NotNull SlashCommandContext commandContext) throws DiscordException {
        return commandContext.reply(
            commandContext.buildResponse()
                .withTimeToLive(30)
                .onCreate(context -> context.edit(response -> response.editCurrentPage(builder -> builder.withContent("created via onCreate"))))
                .withPages(Page.builder().withContent("waiting for create").build())
                .build()
        );
    }

}
