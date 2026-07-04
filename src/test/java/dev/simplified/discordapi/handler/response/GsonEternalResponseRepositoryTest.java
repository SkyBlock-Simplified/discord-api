package dev.simplified.discordapi.handler.response;

import dev.simplified.collection.Concurrent;
import discord4j.common.util.Snowflake;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the file-backed {@link GsonEternalResponseRepository} round-trips a record through the
 * flat DTO and survives a reload (a proxy for a bot restart), including the navigation coordinate.
 */
class GsonEternalResponseRepositoryTest {

    private static EternalResponseRecord sampleRecord() {
        NavState navState = new NavState(Optional.of("page2"), 3, Concurrent.newList("home", "page2"));
        return new EternalResponseRecord(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            Snowflake.of(800000000000000001L),
            Snowflake.of(222222222222222222L),
            Optional.of(Snowflake.of(208023865127862272L)),
            Snowflake.of(333333333333333333L),
            "leaderboard",
            "guild-42",
            navState,
            Instant.ofEpochMilli(1_000_000L),
            Instant.ofEpochMilli(2_000_000L)
        );
    }

    @Test
    void round_trips_a_record_across_reload(@TempDir Path dir) {
        Path file = dir.resolve("eternal.json");
        EternalResponseRecord record = sampleRecord();

        GsonEternalResponseRepository.of(file).save(record).block();

        // A fresh instance loads the persisted file - the record is byte-for-byte equal (incl. NavState).
        GsonEternalResponseRepository reloaded = GsonEternalResponseRepository.of(file);
        assertEquals(record, reloaded.findByResponseId(record.responseId()).block(),
            "the record should round-trip by response id");
        assertEquals(record, reloaded.findByMessage(record.messageId()).block(),
            "the record should round-trip by message id");
    }

    @Test
    void persists_a_nav_state_update(@TempDir Path dir) {
        Path file = dir.resolve("eternal.json");
        EternalResponseRecord record = sampleRecord();
        GsonEternalResponseRepository repo = GsonEternalResponseRepository.of(file);
        repo.save(record).block();

        NavState moved = new NavState(Optional.of("home"), 0, Concurrent.newList("home"));
        repo.saveNavState(record.responseId(), moved).block();

        EternalResponseRecord reloaded = GsonEternalResponseRepository.of(file)
            .findByResponseId(record.responseId())
            .block();
        assertNotNull(reloaded);
        assertEquals(moved, reloaded.navState(), "the updated nav coordinate should persist across a reload");
    }

    @Test
    void delete_removes_from_disk(@TempDir Path dir) {
        Path file = dir.resolve("eternal.json");
        EternalResponseRecord record = sampleRecord();
        GsonEternalResponseRepository repo = GsonEternalResponseRepository.of(file);
        repo.save(record).block();
        repo.delete(record.responseId()).block();

        assertNull(GsonEternalResponseRepository.of(file).findByResponseId(record.responseId()).block(),
            "a deleted record should not reload");
    }

}
