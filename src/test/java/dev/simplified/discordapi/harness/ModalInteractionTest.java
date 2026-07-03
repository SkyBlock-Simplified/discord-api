package dev.simplified.discordapi.harness;

import dev.simplified.discordapi.harness.command.ModalCommand;
import dev.simplified.discordapi.harness.data.TestIds;
import dev.simplified.discordapi.harness.rest.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M4 modal vertical: a button opens a modal (registering it on the cached response), and a simulated
 * modal submit drives {@code ModalListener} -> the modal's {@code onInteract} -> edit, fully offline.
 */
class ModalInteractionTest {

    @Test
    void modal_submit_runs_callback_offline() {
        try (OfflineHarness harness = new OfflineHarness().boot(Duration.ofSeconds(30))) {
            // Plant the message with the open-modal button.
            harness.sendSlashCommand("modal");
            harness.awaitRequest(
                request -> request.path().contains("interaction-token-modal") && request.path().endsWith("/messages/@original"),
                Duration.ofSeconds(10)
            );

            // Click the button -> presentModal registers the modal on the cached entry (callback type 9).
            harness.clickButton(TestIds.REPLY_MESSAGE_ID, ModalCommand.OPEN_BUTTON_ID);
            harness.awaitRequest(
                request -> request.method().equals("POST") && request.path().contains("open_modal") && request.path().endsWith("/callback"),
                Duration.ofSeconds(10)
            );

            // Submit the modal -> ModalListener matches it and runs onInteract, which edits the content.
            harness.submitModal(TestIds.REPLY_MESSAGE_ID, ModalCommand.MODAL_ID, ModalCommand.INPUT_ID, "hello world");

            // The modal edit goes out as a component callback (type 7 update); assert the submitted text
            // input value folded through into the edited content.
            RecordedRequest edit = harness.awaitRequest(
                request -> request.path().contains("modal-token") && request.body().contains("modal:"),
                Duration.ofSeconds(10)
            );

            assertTrue(
                edit.body().contains("modal: hello world"),
                "modal submit should fold the input value into the content; body=" + edit.body()
            );
        }
    }

}
