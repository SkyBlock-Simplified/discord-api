package dev.simplified.discordapi.harness.test;

import dev.simplified.discordapi.harness.OfflineHarness;
import dev.simplified.discordapi.harness.command.ButtonCommand;
import dev.simplified.discordapi.harness.data.TestIds;
import dev.simplified.discordapi.harness.rest.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M3 component vertical: a simulated button click drives the real pathway ({@code ComponentListener} ->
 * cached response lookup -> the button's {@code onInteract} callback -> edit) fully offline. First a
 * {@code /button} slash reply plants the clickable message, then the click edits it to {@code clicked}.
 */
class ComponentInteractionTest {

    @Test
    void button_click_runs_callback_offline() {
        try (OfflineHarness harness = new OfflineHarness().boot(Duration.ofSeconds(30))) {
            // Plant the message that carries the button.
            harness.sendSlashCommand("button");
            harness.awaitRequest(
                request -> request.method().equals("PATCH")
                    && request.path().contains("interaction-token-button")
                    && request.path().endsWith("/messages/@original"),
                Duration.ofSeconds(10)
            );

            // Click it - routes through ComponentListener to the button's onInteract callback.
            harness.clickButton(TestIds.REPLY_MESSAGE_ID, ButtonCommand.BUTTON_ID);

            RecordedRequest edit = harness.awaitRequest(
                request -> request.method().equals("PATCH")
                    && request.path().contains("button-token")
                    && request.path().endsWith("/messages/@original"),
                Duration.ofSeconds(10)
            );

            assertTrue(
                edit.body().contains("clicked"),
                "button click should edit the content to 'clicked'; body=" + edit.body()
            );

            // Discord permits exactly one interaction callback - regression guard against the double-ACK.
            assertEquals(1, harness.callbackCount("button-token-" + ButtonCommand.BUTTON_ID),
                "the button click must acknowledge exactly once");
        }
    }

}
