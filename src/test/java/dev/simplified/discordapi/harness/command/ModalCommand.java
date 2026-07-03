package dev.simplified.discordapi.harness.command;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.Structure;
import dev.simplified.discordapi.component.interaction.Button;
import dev.simplified.discordapi.component.interaction.Modal;
import dev.simplified.discordapi.component.interaction.TextInput;
import dev.simplified.discordapi.component.layout.ActionRow;
import dev.simplified.discordapi.component.layout.Label;
import dev.simplified.discordapi.context.command.SlashCommandContext;
import dev.simplified.discordapi.exception.DiscordException;
import dev.simplified.discordapi.response.page.Page;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * A global {@code /modal} slash command whose button opens a modal; submitting the modal edits the message
 * content to {@code modal: <input>} - exercising the button -> presentModal -> modal-submit callback path.
 */
@Structure(
    name = "modal",
    description = "Opens a modal with a text input"
)
public class ModalCommand extends DiscordCommand<SlashCommandContext> {

    /** The button that opens the modal. */
    public static final @NotNull String OPEN_BUTTON_ID = "open_modal";
    /** The modal's custom id (referenced by the modal-submit interaction). */
    public static final @NotNull String MODAL_ID = "modal_test";
    /** The text input's identifier. */
    public static final @NotNull String INPUT_ID = "feedback";

    public ModalCommand(@NotNull DiscordBot discordBot) {
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
                        .withContent("open the modal")
                        .withComponents(
                            ActionRow.of(
                                Button.builder()
                                    .withStyle(Button.Style.SUCCESS)
                                    .withLabel("Open")
                                    .withIdentifier(OPEN_BUTTON_ID)
                                    .onInteract(buttonContext -> buttonContext.presentModal(
                                        Modal.builder()
                                            .withTitle("Feedback")
                                            .withIdentifier(MODAL_ID)
                                            .withComponents(
                                                Label.builder()
                                                    .withTitle("Your feedback")
                                                    .withComponent(
                                                        TextInput.builder()
                                                            .withStyle(TextInput.Style.SHORT)
                                                            .withIdentifier(INPUT_ID)
                                                            .isRequired(false)
                                                            .build()
                                                    )
                                                    .build()
                                            )
                                            .onInteract(context -> context.edit(response -> response.editCurrentPage(builder -> builder.withContent(
                                                "modal: " + context.getComponent()
                                                    .findComponent(TextInput.class, TextInput::getIdentifier, INPUT_ID)
                                                    .flatMap(TextInput::getValue)
                                                    .orElse("none")
                                            ))))
                                            .build()
                                    ))
                                    .build()
                            )
                        )
                        .build()
                )
                .build()
        );
    }

}
