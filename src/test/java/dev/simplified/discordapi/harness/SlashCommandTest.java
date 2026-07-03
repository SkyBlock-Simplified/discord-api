package dev.simplified.discordapi.harness;

import dev.simplified.discordapi.harness.rest.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M2 slash vertical: a simulated {@code /ping} interaction drives the real pathway
 * ({@code SlashCommandListener} -> {@code DiscordCommand.apply} -> {@code process}) and the reply is
 * captured on the localhost REST mock, entirely offline.
 */
class SlashCommandTest {

    @Test
    void slash_command_replies_offline() {
        try (OfflineHarness harness = new OfflineHarness().boot(Duration.ofSeconds(30))) {
            harness.sendSlashCommand("ping");

            // apply() defers first (interaction callback), then process() replies via webhook edit.
            harness.awaitRequest(
                request -> request.method().equals("POST") && request.path().endsWith("/callback"),
                Duration.ofSeconds(10)
            );

            RecordedRequest edit = harness.awaitRequest(
                request -> request.method().equals("PATCH") && request.path().endsWith("/messages/@original"),
                Duration.ofSeconds(10)
            );

            assertTrue(
                edit.body().contains("pong"),
                "reply body should contain the command's content; body=" + edit.body()
            );
        }
    }

}
