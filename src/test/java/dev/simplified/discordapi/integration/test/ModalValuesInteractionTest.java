package dev.simplified.discordapi.integration.test;

import dev.simplified.discordapi.integration.IntegrationHarness;
import dev.simplified.discordapi.integration.command.ModalValuesCommand;
import dev.simplified.discordfauxrig.rest.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4 vertical: a modal containing a {@link dev.simplified.discordapi.component.interaction.RadioGroup}
 * and a {@link dev.simplified.discordapi.component.interaction.Checkbox} - now pure modal-submit values, not
 * event sources - folds their submitted state through on submit (like {@code TextInput}), and the modal
 * handler observes them.
 */
class ModalValuesInteractionTest {

    @Test
    void modal_folds_radio_and_checkbox_values() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            // Plant the message with the open-modal button.
            harness.sendSlashCommand("modalvalues");
            harness.awaitRequest(
                request -> request.path().contains("interaction-token-modalvalues") && request.path().endsWith("/messages/@original"),
                Duration.ofSeconds(10)
            );

            // Click the button -> presentModal registers the modal on the cached entry.
            harness.clickButton(harness.config().getReplyMessageId(), ModalValuesCommand.OPEN_BUTTON_ID);
            harness.awaitRequest(
                request -> request.method().equals("POST") && request.path().contains(ModalValuesCommand.OPEN_BUTTON_ID) && request.path().endsWith("/callback"),
                Duration.ofSeconds(10)
            );

            // Submit the modal with a radio value (blue) and a checked checkbox.
            harness.gateway().emit(harness.dispatches().modalSubmitValues(
                harness.config().getReplyMessageId(),
                ModalValuesCommand.MODAL_ID,
                ModalValuesCommand.RADIO_ID,
                "blue",
                ModalValuesCommand.CHECK_ID,
                true
            ));

            RecordedRequest edit = harness.awaitRequest(
                request -> request.path().contains("modal-token-" + ModalValuesCommand.MODAL_ID) && request.body().contains("radio:"),
                Duration.ofSeconds(10)
            );

            assertTrue(
                edit.body().contains("radio: blue check: true"),
                "the modal should fold the radio and checkbox values into the content; body=" + edit.body()
            );
            assertEquals(1, harness.callbackCount("modal-token-" + ModalValuesCommand.MODAL_ID),
                "the modal submit must acknowledge exactly once");
        }
    }

}
