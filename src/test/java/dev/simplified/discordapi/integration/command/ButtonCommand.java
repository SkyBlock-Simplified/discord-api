package dev.simplified.discordapi.integration.command;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.Structure;
import dev.simplified.discordapi.component.interaction.Button;
import dev.simplified.discordapi.component.layout.ActionRow;
import dev.simplified.discordapi.context.command.SlashCommandContext;
import dev.simplified.discordapi.exception.DiscordException;
import dev.simplified.discordapi.response.page.Page;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * A global {@code /button} slash command for the offline harness that replies with a single button whose
 * click edits the message content to {@code clicked} - exercising the component interaction callback path.
 */
@Structure(
    name = "button",
    description = "Replies with a clickable button"
)
public class ButtonCommand extends DiscordCommand<SlashCommandContext> {

    /** Explicit component id so a simulated click can target the button deterministically. */
    public static final @NotNull String BUTTON_ID = "btn_ping";

    public ButtonCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** {@inheritDoc} */
    @Override
    protected @NotNull Mono<Void> process(@NotNull SlashCommandContext commandContext) throws DiscordException {
        return commandContext.reply(
            commandContext.buildResponse()
                .withTimeToLive(30)
                .withPages(
                    Page.builder()
                        .withContent("press it")
                        .withComponents(
                            ActionRow.of(
                                Button.builder()
                                    .withStyle(Button.Style.PRIMARY)
                                    .withLabel("Ping")
                                    .withIdentifier(BUTTON_ID)
                                    .withDeferEdit()
                                    .onInteract(context -> context.edit(response -> response.editCurrentPage(builder -> builder.withContent("clicked"))))
                                    .build()
                            )
                        )
                        .build()
                )
                .build()
        );
    }

}
