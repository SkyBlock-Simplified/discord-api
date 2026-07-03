package dev.simplified.discordapi.harness.test;

import dev.simplified.discordapi.harness.OfflineHarness;
import dev.simplified.discordapi.harness.rest.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C3 message-create vertical: a cached response's {@code onCreate} handler runs when a bot-authored
 * {@code MESSAGE_CREATE} for its message arrives, driving {@code MessageCreateListener} -> onCreate -> edit.
 */
class MessageCreateTest {

    @Test
    void on_create_handler_runs_offline() {
        try (OfflineHarness harness = new OfflineHarness().boot(Duration.ofSeconds(30))) {
            // Plant a response that carries an onCreate handler.
            harness.sendSlashCommand("oncreate");
            harness.awaitRequest(
                request -> request.path().contains("interaction-token-oncreate") && request.path().endsWith("/messages/@original"),
                Duration.ofSeconds(10)
            );

            // Simulate the message-create for that message -> onCreate edits the channel message.
            harness.emitMessageCreate(harness.config().getReplyMessageId());

            RecordedRequest edit = harness.awaitRequest(
                request -> request.method().equals("PATCH")
                    && request.path().equals("/channels/" + harness.config().getChannelId() + "/messages/" + harness.config().getReplyMessageId()),
                Duration.ofSeconds(10)
            );

            assertTrue(
                edit.body().contains("created via onCreate"),
                "onCreate should edit the channel message; body=" + edit.body()
            );
        }
    }

}
