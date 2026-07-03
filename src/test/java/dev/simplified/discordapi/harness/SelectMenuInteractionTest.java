package dev.simplified.discordapi.harness;

import dev.simplified.discordapi.harness.command.SelectMenuCommand;
import dev.simplified.discordapi.harness.data.TestIds;
import dev.simplified.discordapi.harness.rest.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Select-menu vertical: a simulated string select interaction drives the real pathway
 * ({@code ComponentListener} -> cached lookup -> {@code SelectMenu.updateSelected} -> the menu's
 * {@code onInteract} -> edit) fully offline, and the handler observes the chosen value.
 */
class SelectMenuInteractionTest {

    @Test
    void select_menu_folds_value_and_runs_callback_offline() {
        try (OfflineHarness harness = new OfflineHarness().boot(Duration.ofSeconds(30))) {
            // Plant the message that carries the select menu.
            harness.sendSlashCommand("select");
            harness.awaitRequest(
                request -> request.method().equals("PATCH")
                    && request.path().contains("interaction-token-select")
                    && request.path().endsWith("/messages/@original"),
                Duration.ofSeconds(10)
            );

            // Choose "blue" - routes through ComponentListener to the menu's onInteract callback.
            harness.clickSelectMenu(TestIds.REPLY_MESSAGE_ID, SelectMenuCommand.SELECT_ID, "blue");

            RecordedRequest edit = harness.awaitRequest(
                request -> request.path().contains("select-token-" + SelectMenuCommand.SELECT_ID)
                    && request.body().contains("selected: blue"),
                Duration.ofSeconds(10)
            );

            assertTrue(
                edit.body().contains("selected: blue"),
                "the select menu should fold the chosen value into the content; body=" + edit.body()
            );
            assertEquals(1, harness.callbackCount("select-token-" + SelectMenuCommand.SELECT_ID),
                "the select interaction must acknowledge exactly once");
        }
    }

}
