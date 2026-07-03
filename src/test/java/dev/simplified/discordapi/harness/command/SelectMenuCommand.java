package dev.simplified.discordapi.harness.command;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.Structure;
import dev.simplified.discordapi.component.interaction.SelectMenu;
import dev.simplified.discordapi.component.layout.ActionRow;
import dev.simplified.discordapi.context.command.SlashCommandContext;
import dev.simplified.discordapi.exception.DiscordException;
import dev.simplified.discordapi.response.page.Page;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * A global {@code /select} slash command for the offline harness that replies with a string select menu
 * whose selection edits the message content to {@code selected: <value>} - exercising the select-menu
 * component interaction callback path (value folding via {@code updateSelected}).
 */
@Structure(
    name = "select",
    description = "Replies with a string select menu"
)
public class SelectMenuCommand extends DiscordCommand<SlashCommandContext> {

    /** Explicit component id so a simulated selection can target the menu deterministically. */
    public static final @NotNull String SELECT_ID = "pick_color";

    public SelectMenuCommand(@NotNull DiscordBot discordBot) {
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
                        .withContent("pick one")
                        .withComponents(
                            ActionRow.of(
                                SelectMenu.builder()
                                    .withIdentifier(SELECT_ID)
                                    .withOptions(
                                        SelectMenu.Option.builder().withLabel("Red").withValue("red").build(),
                                        SelectMenu.Option.builder().withLabel("Blue").withValue("blue").build()
                                    )
                                    .onInteract(context -> context.edit(response -> response.editCurrentPage(builder -> builder.withContent(
                                        "selected: " + context.getSelectedValues().stream().findFirst().orElse("none")
                                    ))))
                                    .build()
                            )
                        )
                        .build()
                )
                .build()
        );
    }

}
