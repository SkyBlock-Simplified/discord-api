package dev.simplified.discordapi.harness.test;

import dev.simplified.discordapi.harness.OfflineHarness;
import dev.simplified.discordapi.harness.rest.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * [H1] regression: a command that replies with a bare {@code Response.builder()} (no bot) drives the real
 * pathway ({@code SlashCommandListener} -> {@code process} -> reply). The rendering context injects the emoji
 * resolver, so the botless response builds and renders end-to-end - what used to NPE at build time.
 */
class BareResponseTest {

    @Test
    void bare_botless_response_replies_offline() {
        try (OfflineHarness harness = new OfflineHarness().boot(Duration.ofSeconds(30))) {
            harness.sendSlashCommand("bare");

            harness.awaitInteractionCallback();
            RecordedRequest reply = harness.awaitInteractionReply();

            assertTrue(reply.bodyContains("bare ok"), "botless response should reply; body=" + reply.body());
        }
    }

}
