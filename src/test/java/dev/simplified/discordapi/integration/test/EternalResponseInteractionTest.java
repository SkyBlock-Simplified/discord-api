package dev.simplified.discordapi.integration.test;

import dev.simplified.discordapi.handler.response.EternalResponseRepository;
import dev.simplified.discordapi.handler.response.GsonEternalResponseRepository;
import dev.simplified.discordapi.handler.response.InMemoryEternalResponseRepository;
import dev.simplified.discordapi.harness.HarnessConfig;
import dev.simplified.discordapi.integration.IntegrationHarness;
import dev.simplified.discordapi.integration.command.EternalButtonCommand;
import dev.simplified.discordapi.harness.rest.RecordedRequest;
import discord4j.common.util.Snowflake;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the eternal (reboot-surviving) response flow end to end.
 * <ul>
 *   <li><b>reboot survival</b> - an eternal message created in one boot is transparently rehydrated
 *       from the shared cold store in a fresh boot; the click dispatches the real {@code @Component}
 *       handler against a real rebuilt component</li>
 *   <li><b>drop on unknown</b> - a click on a message with neither a hot entry nor a cold record is
 *       dropped (deferred once), never running a handler</li>
 * </ul>
 */
class EternalResponseInteractionTest {

    /** A message id that has neither a hot entry nor a cold record, forcing the drop path. */
    private static final long UNKNOWN_MESSAGE_ID = 888800000000000099L;

    /** Distinct reply id for the second boot, so its warm-up reply cannot collide with the eternal message id. */
    private static final long SECOND_BOOT_REPLY_ID = 800000000000000077L;

    @Test
    void eternal_survives_reboot_with_in_memory_backend() {
        // In-memory has no file, so the same instance IS the "surviving" store across boots.
        EternalResponseRepository shared = InMemoryEternalResponseRepository.of();
        runRebootScenario(() -> shared);
    }

    @Test
    void eternal_survives_reboot_with_gson_file_backend(@TempDir Path dir) {
        // A fresh Gson repo per boot over the same file: boot #2 loads what boot #1 wrote to disk.
        Path file = dir.resolve("eternal.json");
        runRebootScenario(() -> GsonEternalResponseRepository.of(file));
    }

    /**
     * Creates an eternal message in one boot, then re-hydrates and dispatches it in a fresh boot.
     * Each boot gets its own repository from the factory over the same backing store, so a
     * file-backed factory genuinely proves cross-restart survival through disk.
     */
    private static void runRebootScenario(@NotNull Supplier<EternalResponseRepository> coldStoreFactory) {
        long eternalMessageId = HarnessConfig.builder().build().getReplyMessageId();

        // Boot #1: create the eternal message; it writes through to the cold store.
        EternalResponseRepository firstBootStore = coldStoreFactory.get();
        try (IntegrationHarness harness = new IntegrationHarness(HarnessConfig.builder().build(), firstBootStore).boot(Duration.ofSeconds(30))) {
            harness.sendSlashCommand("eternal");
            harness.awaitInteractionReply();
            awaitColdRecord(firstBootStore, eternalMessageId, Duration.ofSeconds(10));
        }

        // Boot #2: a fresh hot tier + a fresh cold store over the same backing store, with a distinct
        // reply id so the warm-up reply is cached under a different message than the eternal one.
        HarnessConfig secondBoot = HarnessConfig.builder().withReplyMessageId(SECOND_BOOT_REPLY_ID).build();
        try (IntegrationHarness harness = new IntegrationHarness(secondBoot, coldStoreFactory.get()).boot(Duration.ofSeconds(30))) {
            // Warm the listener pipeline up (registers the component listener alongside the slash listener).
            harness.sendSlashCommand("annotated");
            harness.awaitInteractionReply();
            harness.awaitComponentRoute(EternalButtonCommand.BUTTON_ID, Duration.ofSeconds(10));

            // Click the persisted button on the cold-only eternal message - it is not in the hot tier.
            harness.gateway().emit(harness.dispatches().button(eternalMessageId, EternalButtonCommand.BUTTON_ID));

            // Hydration rebuilds the REAL button and dispatches the @Component handler, which edits the content.
            RecordedRequest edit = harness.awaitRequest(
                request -> request.body().contains(EternalButtonCommand.HANDLED_CONTENT),
                Duration.ofSeconds(10)
            );
            assertTrue(
                edit.body().contains(EternalButtonCommand.HANDLED_CONTENT),
                "the hydrated eternal click should edit the content; body=" + edit.body()
            );
            assertEquals(1, harness.callbackCount("button-token-" + EternalButtonCommand.BUTTON_ID),
                "the hydrated click must acknowledge exactly once");
        }
    }

    @Test
    void unknown_message_with_no_record_is_dropped() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            // Warm the listener pipeline up.
            harness.sendSlashCommand("annotated");
            harness.awaitInteractionReply();
            harness.awaitComponentRoute(EternalButtonCommand.BUTTON_ID, Duration.ofSeconds(10));

            // Click on a message that has neither a hot entry nor a cold record.
            harness.gateway().emit(harness.dispatches().button(UNKNOWN_MESSAGE_ID, EternalButtonCommand.BUTTON_ID));

            // A drop defers exactly once via a callback and never runs the handler.
            harness.awaitRequest(
                request -> request.method().equals("POST")
                    && request.path().contains("button-token-" + EternalButtonCommand.BUTTON_ID)
                    && request.path().endsWith("/callback"),
                Duration.ofSeconds(10)
            );
            assertTrue(
                harness.requests().stream().noneMatch(request -> request.body().contains(EternalButtonCommand.HANDLED_CONTENT)),
                "a dropped click must never run the handler"
            );
            assertEquals(1, harness.callbackCount("button-token-" + EternalButtonCommand.BUTTON_ID),
                "the dropped click defers exactly once");
        }
    }

    /** Polls the cold store until a record for the given message is present, or fails after the timeout. */
    @SuppressWarnings("BusyWait")
    private static void awaitColdRecord(@NotNull EternalResponseRepository store, long messageId, @NotNull Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            if (store.findByMessage(Snowflake.of(messageId)).block(Duration.ofSeconds(1)) != null)
                return;

            try {
                Thread.sleep(25);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        throw new AssertionError("eternal record for message " + messageId + " was not persisted within " + timeout);
    }

}
