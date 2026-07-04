package dev.simplified.discordapi.integration.command;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.Structure;
import dev.simplified.discordapi.component.interaction.Button;
import dev.simplified.discordapi.component.interaction.Checkbox;
import dev.simplified.discordapi.component.interaction.Modal;
import dev.simplified.discordapi.component.interaction.RadioGroup;
import dev.simplified.discordapi.component.layout.ActionRow;
import dev.simplified.discordapi.component.layout.Label;
import dev.simplified.discordapi.context.command.SlashCommandContext;
import dev.simplified.discordapi.exception.DiscordException;
import dev.simplified.discordapi.response.page.Page;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * A global {@code /modalvalues} slash command whose button opens a modal containing a
 * {@link RadioGroup} and a {@link Checkbox} - non-text-input modal-submit values. Submitting the modal
 * edits the content to {@code radio: <value> check: <bool>}, verifying those components fold their
 * submitted state through the modal (like {@code TextInput}) rather than emitting their own events.
 */
@Structure(
    name = "modalvalues",
    description = "Opens a modal with a radio group and a checkbox"
)
public class ModalValuesCommand extends DiscordCommand<SlashCommandContext> {

    /** The button that opens the modal. */
    public static final @NotNull String OPEN_BUTTON_ID = "open_values_modal";
    /** The modal's custom id. */
    public static final @NotNull String MODAL_ID = "values_modal";
    /** The radio group's custom id. */
    public static final @NotNull String RADIO_ID = "fav_color";
    /** The checkbox's custom id. */
    public static final @NotNull String CHECK_ID = "agree";

    public ModalValuesCommand(@NotNull DiscordBot discordBot) {
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
                                            .withTitle("Preferences")
                                            .withIdentifier(MODAL_ID)
                                            .withComponents(
                                                // Leads with a radio group (no text input): exercises the H5 fix
                                                // where Modal.getInteraction no longer casts the first label
                                                // component to TextInput.
                                                Label.builder()
                                                    .withTitle("Favorite color")
                                                    .withComponent(
                                                        RadioGroup.builder()
                                                            .withIdentifier(RADIO_ID)
                                                            .withOptions(
                                                                RadioGroup.Option.builder().withLabel("Red").withValue("red").build(),
                                                                RadioGroup.Option.builder().withLabel("Blue").withValue("blue").build()
                                                            )
                                                            .build()
                                                    )
                                                    .build(),
                                                Label.builder()
                                                    .withTitle("Agree to terms")
                                                    .withComponent(
                                                        Checkbox.builder()
                                                            .withIdentifier(CHECK_ID)
                                                            .build()
                                                    )
                                                    .build()
                                            )
                                            .onSubmit(context -> context.edit(response -> response.editCurrentPage(builder -> builder.withContent(
                                                "radio: " + context.getComponent()
                                                    .findComponent(RadioGroup.class, RadioGroup::getIdentifier, RADIO_ID)
                                                    .flatMap(RadioGroup::getSelected)
                                                    .map(RadioGroup.Option::getValue)
                                                    .orElse("none")
                                                    + " check: " + context.getComponent()
                                                    .findComponent(Checkbox.class, Checkbox::getIdentifier, CHECK_ID)
                                                    .map(Checkbox::isSelected)
                                                    .orElse(false)
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
