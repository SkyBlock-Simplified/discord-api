package dev.simplified.discordapi.integration.test;

import dev.simplified.discordapi.harness.rest.RecordedRequest;
import dev.simplified.discordapi.harness.rest.RenderedMessage;
import dev.simplified.discordapi.integration.IntegrationHarness;
import dev.simplified.discordapi.integration.command.EditorAggregateCommand;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the {@link EditorAggregateCommand} live editor through the offline harness: renders it, then edits
 * each field kind through its modal or in-page escalation and confirms each interaction is sent exactly once,
 * the live model updates, and the message re-renders in place. Also exercises cancel and confirmed delete.
 */
class EditorAggregateInteractionTest {

    private static final String COMMAND = "editaggregate";

    private static final String EDIT_NAME = "editor:agg:name";
    private static final String EDIT_LEVEL = "editor:agg:level";
    private static final String EDIT_ACTIVE = "editor:agg:active";
    private static final String EDIT_COLOR = "editor:agg:color";
    private static final String EDIT_BIG = "editor:agg:big";
    private static final String EDIT_NICKNAME = "editor:agg:nickname";

    @Test
    void renders_an_edit_button_per_field() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            harness.sendSlashCommand(COMMAND);
            RenderedMessage rendered = RenderedMessage.of(harness.awaitInteractionReply());

            assertTrue(rendered.textContains("Edit widget"), "the header should render");
            assertTrue(rendered.hasComponent(EDIT_NAME), "name field edit button");
            assertTrue(rendered.hasComponent(EDIT_LEVEL), "level field edit button");
            assertTrue(rendered.hasComponent(EDIT_ACTIVE), "active field edit button");
            assertTrue(rendered.hasComponent(EDIT_COLOR), "color field edit button");
            assertTrue(rendered.hasComponent(EDIT_BIG), "big field edit button");
            assertTrue(rendered.hasComponent(EDIT_NICKNAME), "nickname field edit button");
            assertTrue(rendered.hasComponent("editor:agg:cancel"), "cancel action button");
            assertTrue(rendered.hasComponent("editor:agg:delete"), "delete action button");
        }
    }

    @Test
    void text_field_edit_live_saves_and_rerenders() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            long message = harness.config().getReplyMessageId();
            harness.sendSlashCommand(COMMAND);
            harness.awaitInteractionReply();

            harness.clickButton(message, EDIT_NAME);
            harness.submitModal(message, EDIT_NAME + ":modal").withText(EDIT_NAME + ":input", "Trinity").submit();

            RenderedMessage rendered = RenderedMessage.of(reRenderOfModal(harness, EDIT_NAME));
            assertTrue(rendered.textContains("Trinity"), "the re-render should show the new name");
            assertEquals("Trinity", loaded(harness).getModel().name, "the live model should be updated");
            assertEquals(1, harness.callbackCount("modal-token-" + EDIT_NAME + ":modal"), "the modal submit acknowledges exactly once");
            assertEquals(1, harness.callbackCount("button-token-" + EDIT_NAME), "the edit-button click (presentModal) acknowledges exactly once");
        }
    }

    @Test
    void numeric_field_edit_accepts_a_valid_number() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            long message = harness.config().getReplyMessageId();
            harness.sendSlashCommand(COMMAND);
            harness.awaitInteractionReply();

            harness.clickButton(message, EDIT_LEVEL);
            harness.submitModal(message, EDIT_LEVEL + ":modal").withText(EDIT_LEVEL + ":input", "42").submit();

            RenderedMessage rendered = RenderedMessage.of(reRenderOfModal(harness, EDIT_LEVEL));
            assertTrue(rendered.textContains("42"), "the re-render should show the new level");
            assertEquals(42, loaded(harness).getModel().level, "the live model should be updated");
            assertEquals(1, harness.callbackCount("modal-token-" + EDIT_LEVEL + ":modal"), "exactly one modal callback");
        }
    }

    @Test
    void numeric_field_edit_rejects_an_invalid_number() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            long message = harness.config().getReplyMessageId();
            harness.sendSlashCommand(COMMAND);
            harness.awaitInteractionReply();

            harness.clickButton(message, EDIT_LEVEL);
            harness.submitModal(message, EDIT_LEVEL + ":modal").withText(EDIT_LEVEL + ":input", "not-a-number").submit();

            // the invalid input is rejected (InputException path); the submit is still acknowledged, but the
            // value never changes.
            harness.awaitRequest(request -> request.path().contains("modal-token-" + EDIT_LEVEL + ":modal"));
            assertEquals(5, loaded(harness).getModel().level, "an invalid numeric input must not change the value");
        }
    }

    @Test
    void bool_field_edit_folds_the_radio_selection() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            long message = harness.config().getReplyMessageId();
            harness.sendSlashCommand(COMMAND);
            harness.awaitInteractionReply();

            harness.clickButton(message, EDIT_ACTIVE);
            harness.submitModal(message, EDIT_ACTIVE + ":modal").withRadio(EDIT_ACTIVE + ":bool", "true").submit();

            RenderedMessage rendered = RenderedMessage.of(reRenderOfModal(harness, EDIT_ACTIVE));
            assertTrue(rendered.textContains("**Active**\ntrue"), "the re-render should show the boolean value");
            assertTrue(loaded(harness).getModel().active, "the live model should be updated");
            assertEquals(1, harness.callbackCount("modal-token-" + EDIT_ACTIVE + ":modal"), "exactly one modal callback");
        }
    }

    @Test
    void bounded_choice_field_edit_folds_the_select_pick() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            long message = harness.config().getReplyMessageId();
            harness.sendSlashCommand(COMMAND);
            harness.awaitInteractionReply();

            harness.clickButton(message, EDIT_COLOR);
            // the select option value is the choice label; picking "Green" resolves to the value "green"
            harness.submitModal(message, EDIT_COLOR + ":modal").withSelect(EDIT_COLOR + ":choice", "Green").submit();

            RenderedMessage rendered = RenderedMessage.of(reRenderOfModal(harness, EDIT_COLOR));
            assertTrue(rendered.textContains("green"), "the re-render should show the chosen value");
            assertEquals("green", loaded(harness).getModel().color, "the live model should hold the resolved value");
            assertEquals(1, harness.callbackCount("modal-token-" + EDIT_COLOR + ":modal"), "exactly one modal callback");
        }
    }

    @Test
    void large_choice_field_escalates_in_page_and_applies_a_pick() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            long message = harness.config().getReplyMessageId();
            harness.sendSlashCommand(COMMAND);
            harness.awaitInteractionReply();

            // clicking Edit on a > 25-option choice escalates in-page rather than presenting a modal
            harness.clickButton(message, EDIT_BIG);
            RenderedMessage escalated = RenderedMessage.of(harness.awaitRequest(
                request -> request.path().contains("button-token-" + EDIT_BIG) && request.body().contains(EDIT_BIG + ":select")
            ));
            assertTrue(escalated.hasComponent(EDIT_BIG + ":select"), "the escalation select should appear");
            assertTrue(escalated.hasComponent(EDIT_BIG + ":prev"), "the escalation prev button");
            assertTrue(escalated.hasComponent(EDIT_BIG + ":next"), "the escalation next button");
            assertTrue(escalated.hasComponent(EDIT_BIG + ":cancel"), "the escalation cancel button");
            assertEquals("Page 1 of 2 - pick a value", escalated.placeholder(EDIT_BIG + ":select").orElse(null), "slice 1 placeholder");

            // Next advances to the second 25-option slice
            harness.clickButton(message, EDIT_BIG + ":next");
            RenderedMessage sliceTwo = RenderedMessage.of(harness.awaitRequest(
                request -> request.path().contains("button-token-" + EDIT_BIG + ":next") && request.body().contains("Page 2 of 2")
            ));
            assertEquals("Page 2 of 2 - pick a value", sliceTwo.placeholder(EDIT_BIG + ":select").orElse(null), "next advances the slice");

            // picking an option applies it and tears the escalation rows down
            harness.clickSelectMenu(message, EDIT_BIG + ":select", "Option 27");
            RenderedMessage applied = RenderedMessage.of(harness.awaitRequest(
                request -> request.path().contains("select-token-" + EDIT_BIG + ":select") && request.body().contains("opt27")
            ));
            assertTrue(applied.textContains("opt27"), "the re-render should show the applied value");
            assertFalse(applied.hasComponent(EDIT_BIG + ":select"), "the escalation rows should be gone after applying");
            assertEquals("opt27", loaded(harness).getModel().big, "the live model should hold the applied value");
        }
    }

    @Test
    void nullable_field_seeded_null_edits_successfully() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            long message = harness.config().getReplyMessageId();
            harness.sendSlashCommand(COMMAND);
            harness.awaitInteractionReply();

            // the nickname field is seeded null; editing it must succeed (old value is null)
            harness.clickButton(message, EDIT_NICKNAME);
            harness.submitModal(message, EDIT_NICKNAME + ":modal").withText(EDIT_NICKNAME + ":input", "Nick").submit();

            RenderedMessage rendered = RenderedMessage.of(reRenderOfModal(harness, EDIT_NICKNAME));
            assertTrue(rendered.textContains("Nick"), "the re-render should show the newly set nickname");
            assertEquals("Nick", loaded(harness).getModel().nickname, "a null-seeded field must save");
        }
    }

    @Test
    void cancel_deletes_the_message() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            long message = harness.config().getReplyMessageId();
            harness.sendSlashCommand(COMMAND);
            harness.awaitInteractionReply();

            harness.clickButton(message, "editor:agg:cancel");

            // cancel closes the response by deferring (acknowledging) the interaction, deleting the message, and
            // evicting the cache entry: exactly one interaction callback, so Discord never shows "interaction failed"
            harness.awaitRequest(request -> request.method().equals("DELETE"));
            assertEquals(1, harness.callbackCount("button-token-editor:agg:cancel"), "cancel acknowledges exactly once");
        }
    }

    @Test
    void delete_confirms_then_runs_the_delete_handler() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            long message = harness.config().getReplyMessageId();
            harness.sendSlashCommand(COMMAND);
            harness.awaitInteractionReply();

            // first click surfaces a confirm/go-back row
            harness.clickButton(message, "editor:agg:delete");
            RenderedMessage confirming = RenderedMessage.of(harness.awaitRequest(
                request -> request.path().contains("button-token-editor:agg:delete") && request.body().contains("editor:agg:confirm-delete")
            ));
            assertTrue(confirming.hasComponent("editor:agg:confirm-delete"), "confirm-delete button");
            assertTrue(confirming.hasComponent("editor:agg:cancel-delete"), "go-back button");

            // confirming runs onDelete, acknowledges the interaction, and deletes the message
            harness.clickButton(message, "editor:agg:confirm-delete");
            harness.awaitRequest(request -> request.method().equals("DELETE"));
            assertTrue(loaded(harness).getModel().deleted, "the delete handler should have run");
            assertEquals(1, harness.callbackCount("button-token-editor:agg:confirm-delete"), "confirm-delete acknowledges exactly once");
        }
    }

    private static RecordedRequest reRenderOfModal(IntegrationHarness harness, String editId) {
        return harness.awaitRequest(request -> request.path().contains("modal-token-" + editId + ":modal")
            && request.method().equals("POST"));
    }

    private static EditorAggregateCommand loaded(IntegrationHarness harness) {
        return harness.bot().getCommandHandler().getLoadedCommands().stream()
            .filter(EditorAggregateCommand.class::isInstance)
            .map(EditorAggregateCommand.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError("EditorAggregateCommand not loaded"));
    }

}
