package dev.simplified.discordapi.integration.test;

import dev.simplified.discordapi.integration.IntegrationHarness;
import dev.simplified.discordapi.harness.rest.RecordedRequest;
import dev.simplified.annotations.Log;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1 walking skeleton: proves the bot reaches a fully wired connected state offline (localhost REST
 * mock + fake in-JVM gateway), including the post-connect command and emoji sync, with no network.
 */
@Log
class ConnectionSmokeTest {

    @Test
    void bot_connects_and_syncs_offline() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            // The gateway is connected (login + connect completed against the fakes).
            assertNotNull(harness.bot().getGateway(), "gateway should be connected");

            // Post-connect command sync ran (implies the ConnectEvent handler executed and listeners registered).
            harness.awaitRequest(
                request -> request.method().equals("PUT") && request.path().equals("/applications/" + harness.config().getBotId() + "/commands"),
                Duration.ofSeconds(10)
            );

            // Emoji sync fetches app info then the application emoji list; wait for the emoji list call
            // (its presence proves getApplicationInfo() deserialized and the sync chain did not error).
            harness.awaitRequest(
                request -> request.path().equals("/applications/" + harness.config().getBotId() + "/emojis"),
                Duration.ofSeconds(10)
            );

            java.util.List<String> paths = harness.requests().stream().map(RecordedRequest::path).toList();
            log.info("Recorded request paths: {}", paths);

            assertTrue(paths.contains("/users/@me"), "should have fetched self; paths=" + paths);
            assertTrue(paths.contains("/oauth2/applications/@me"), "should have fetched application info; paths=" + paths);
        }
    }

}
