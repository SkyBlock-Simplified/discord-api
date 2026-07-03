package dev.simplified.discordapi.harness.test;

import dev.simplified.discordapi.harness.OfflineHarness;
import dev.simplified.discordapi.harness.rest.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M5 message/user command vertical: simulated right-click user and message commands drive
 * {@code UserCommandListener} / {@code MessageCommandListener} -> {@code process} -> reply, fully offline.
 */
class MessageUserCommandTest {

    @Test
    void user_command_replies_offline() {
        try (OfflineHarness harness = new OfflineHarness().boot(Duration.ofSeconds(30))) {
            harness.sendUserCommand("greet", harness.config().getUserId());

            RecordedRequest edit = harness.awaitRequest(
                request -> request.method().equals("PATCH")
                    && request.path().contains("user-token-greet")
                    && request.path().endsWith("/messages/@original"),
                Duration.ofSeconds(10)
            );

            assertTrue(edit.body().contains("user command ran"), "user command reply; body=" + edit.body());
        }
    }

    @Test
    void message_command_replies_offline() {
        try (OfflineHarness harness = new OfflineHarness().boot(Duration.ofSeconds(30))) {
            harness.sendMessageCommand("inspect", harness.config().getReplyMessageId());

            RecordedRequest edit = harness.awaitRequest(
                request -> request.method().equals("PATCH")
                    && request.path().contains("message-token-inspect")
                    && request.path().endsWith("/messages/@original"),
                Duration.ofSeconds(10)
            );

            assertTrue(edit.body().contains("message command ran"), "message command reply; body=" + edit.body());
        }
    }

}
