package dev.simplified.discordapi.harness.rest;

import dev.simplified.discordapi.component.Component;
import dev.simplified.discordapi.integration.IntegrationHarness;
import dev.simplified.discordapi.integration.command.ButtonCommand;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates {@link RenderedMessage} against a real reply body captured from the offline harness, so the
 * structural lookups the integration suites rely on are exercised against genuine Discord4J serialization.
 */
class RenderedMessageTest {

    @Test
    void parses_components_and_text_from_a_real_reply_body() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            harness.sendSlashCommand("button");
            RenderedMessage rendered = RenderedMessage.of(harness.awaitInteractionReply());

            // structural: the button is present, carries its label, and renders enabled
            assertTrue(rendered.hasComponent(ButtonCommand.BUTTON_ID), "the reply should carry the button");
            assertEquals("Ping", rendered.label(ButtonCommand.BUTTON_ID).orElse(null), "the button label should be readable");
            assertFalse(rendered.isDisabled(ButtonCommand.BUTTON_ID), "the button should render enabled");

            // discovery by type: the button (type 2) is discoverable by its component type
            assertTrue(
                rendered.customIdsByType(Component.Type.BUTTON.getValue()).contains(ButtonCommand.BUTTON_ID),
                "the button id should be discoverable by component type"
            );

            // text: the page content is part of the rendered text
            assertTrue(rendered.textContains("press it"), "the rendered text should contain the page content");

            // absence: an unrelated id is reported absent
            assertFalse(rendered.hasComponent("no-such-component"), "an unknown component id must be absent");
        }
    }

}
