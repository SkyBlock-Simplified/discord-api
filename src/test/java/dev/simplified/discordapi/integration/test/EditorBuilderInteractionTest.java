package dev.simplified.discordapi.integration.test;

import dev.simplified.discordfauxrig.rest.RenderedMessage;
import dev.simplified.discordapi.integration.IntegrationHarness;
import dev.simplified.discordapi.integration.command.EditorBuilderCommand;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the {@link EditorBuilderCommand} builder-mode editor: edits a field (which accumulates in the seed
 * rather than saving live), submits through the confirmation step, and confirms {@code onSubmit} captured the
 * assembled seed and the message closed.
 */
class EditorBuilderInteractionTest {

    private static final String EDIT_NAME = "editor:bld:name";

    @Test
    void edit_then_confirm_submit_captures_the_assembled_seed() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            long message = harness.config().getReplyMessageId();
            harness.sendSlashCommand("editbuilder");
            harness.awaitInteractionReply();

            // editing a builder field accumulates in the seed (no live save)
            harness.clickButton(message, EDIT_NAME);
            harness.submitModal(message, EDIT_NAME + ":modal").withText(EDIT_NAME + ":input", "Morpheus").submit();
            RenderedMessage edited = RenderedMessage.of(harness.awaitRequest(
                request -> request.path().contains("modal-token-" + EDIT_NAME + ":modal") && request.body().contains("Morpheus")
            ));
            assertTrue(edited.textContains("Morpheus"), "the re-render should show the accumulated value");
            assertTrue(loaded(harness).getSubmitted() == null, "the seed is not submitted until confirmed");

            // Submit surfaces a confirmation row (confirmSubmit)
            harness.clickButton(message, "editor:bld:submit");
            RenderedMessage confirming = RenderedMessage.of(harness.awaitRequest(
                request -> request.path().contains("button-token-editor:bld:submit") && request.body().contains("editor:bld:confirm-submit")
            ));
            assertTrue(confirming.hasComponent("editor:bld:confirm-submit"), "confirm-submit button");
            assertTrue(confirming.hasComponent("editor:bld:cancel-submit"), "go-back button");

            // Confirming runs onSubmit with the assembled seed and closes the message
            harness.clickButton(message, "editor:bld:confirm-submit");
            harness.awaitRequest(request -> request.method().equals("DELETE"));

            EditorBuilderCommand command = loaded(harness);
            assertNotNull(command.getSubmitted(), "onSubmit should have captured the seed");
            assertEquals("Morpheus", command.getSubmitted().name, "the submitted seed carries the accumulated edit");
        }
    }

    private static EditorBuilderCommand loaded(IntegrationHarness harness) {
        return harness.bot().getCommandHandler().getLoadedCommands().stream()
            .filter(EditorBuilderCommand.class::isInstance)
            .map(EditorBuilderCommand.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError("EditorBuilderCommand not loaded"));
    }

}
