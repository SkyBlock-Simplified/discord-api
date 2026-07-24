package dev.simplified.discordapi.handler.response;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.discordapi.handler.DiscordConfig;
import discord4j.common.util.Snowflake;
import dev.simplified.annotations.Log;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * File-backed {@link EternalResponseRepository} that persists records as JSON, so eternal messages
 * survive a restart without a database. A built-in alternative to
 * {@link InMemoryEternalResponseRepository} for single-node bots; heavier deployments plug a durable
 * backend (e.g. Hibernate) via {@link DiscordConfig.Builder#withEternalRepository}.
 *
 * <p>
 * The {@link EternalResponseRecord} mixes types raw Gson mishandles ({@link Snowflake},
 * {@link Instant}, {@link Optional}, {@link NavState}), so it is mapped to and from a flat DTO
 * (snowflakes as strings, instants as epoch millis, optionals as nullables) - the same flat-DTO
 * approach the locale loader uses. The in-memory maps mirror {@link InMemoryEternalResponseRepository};
 * every mutation writes the whole file through atomically (temp file + move).
 */
@Log
public final class GsonEternalResponseRepository implements EternalResponseRepository {

    private static final @NotNull Gson GSON = new Gson();

    private final @NotNull ConcurrentMap<UUID, EternalResponseRecord> records = Concurrent.newMap();
    private final @NotNull ConcurrentMap<Snowflake, UUID> messageIndex = Concurrent.newMap();
    private final @NotNull Object writeLock = new Object();
    private final @NotNull Path file;

    private GsonEternalResponseRepository(@NotNull Path file) {
        this.file = file;
        this.load();
    }

    /**
     * Constructs a repository backed by the given JSON file, loading any existing records.
     *
     * @param file the JSON file to persist records to
     * @return a new file-backed repository
     */
    public static @NotNull GsonEternalResponseRepository of(@NotNull Path file) {
        return new GsonEternalResponseRepository(file);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<EternalResponseRecord> findByMessage(@NotNull Snowflake messageId) {
        return Mono.fromCallable(() -> {
            UUID responseId = this.messageIndex.get(messageId);
            return responseId == null ? null : this.records.get(responseId);
        });
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<EternalResponseRecord> findByResponseId(@NotNull UUID responseId) {
        return Mono.fromCallable(() -> this.records.get(responseId));
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<Void> save(@NotNull EternalResponseRecord record) {
        return Mono.fromRunnable(() -> {
            synchronized (this.writeLock) {
                this.records.put(record.responseId(), record);
                this.messageIndex.put(record.messageId(), record.responseId());
                this.flush();
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<Void> saveNavState(@NotNull UUID responseId, @NotNull NavState navState) {
        return Mono.fromRunnable(() -> {
            synchronized (this.writeLock) {
                EternalResponseRecord existing = this.records.get(responseId);
                if (existing != null) {
                    this.records.put(responseId, existing.withNavState(navState, Instant.now()));
                    this.flush();
                }
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<Void> delete(@NotNull UUID responseId) {
        return Mono.fromRunnable(() -> {
            synchronized (this.writeLock) {
                EternalResponseRecord removed = this.records.remove(responseId);
                if (removed != null) {
                    this.messageIndex.remove(removed.messageId());
                    this.flush();
                }
            }
        });
    }

    /** Loads records from the backing file into the in-memory maps, tolerating a missing or corrupt file. */
    private void load() {
        if (!Files.exists(this.file))
            return;

        try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
            FileDto parsed = GSON.fromJson(reader, FileDto.class);
            if (parsed == null || parsed.records == null)
                return;

            for (RecordDto dto : parsed.records) {
                EternalResponseRecord record = toRecord(dto);
                this.records.put(record.responseId(), record);
                this.messageIndex.put(record.messageId(), record.responseId());
            }
        } catch (JsonSyntaxException | IOException ex) {
            log.warn("Failed to load eternal responses from '{}': {}", this.file, ex.getMessage());
        }
    }

    /** Serializes every record and writes it through atomically (temp file + move) under {@link #writeLock}. */
    private void flush() {
        FileDto fileDto = new FileDto();
        fileDto.records = this.records.values().stream().map(GsonEternalResponseRepository::toDto).toList();

        try {
            Path parent = this.file.getParent();
            if (parent != null)
                Files.createDirectories(parent);

            Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(fileDto), StandardCharsets.UTF_8);
            Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to persist eternal responses to '" + this.file + "'", ex);
        }
    }

    private static @NotNull RecordDto toDto(@NotNull EternalResponseRecord record) {
        RecordDto dto = new RecordDto();
        dto.responseId = record.responseId().toString();
        dto.messageId = record.messageId().asString();
        dto.channelId = record.channelId().asString();
        dto.guildId = record.guildId().map(Snowflake::asString).orElse(null);
        dto.userId = record.userId().asString();
        dto.builderKey = record.builderKey();
        dto.payload = record.payload();
        dto.currentPageId = record.navState().getCurrentPageId().orElse(null);
        dto.currentItemPage = record.navState().getCurrentItemPage();
        dto.pageHistory = record.navState().getPageHistory().stream().toList();
        dto.createdAt = record.createdAt().toEpochMilli();
        dto.updatedAt = record.updatedAt().toEpochMilli();
        return dto;
    }

    private static @NotNull EternalResponseRecord toRecord(@NotNull RecordDto dto) {
        NavState navState = new NavState(
            Optional.ofNullable(dto.currentPageId),
            dto.currentItemPage,
            dto.pageHistory == null ? Concurrent.newList() : Concurrent.newList(dto.pageHistory)
        );

        return new EternalResponseRecord(
            UUID.fromString(dto.responseId),
            Snowflake.of(dto.messageId),
            Snowflake.of(dto.channelId),
            Optional.ofNullable(dto.guildId).map(Snowflake::of),
            Snowflake.of(dto.userId),
            dto.builderKey,
            dto.payload,
            navState,
            Instant.ofEpochMilli(dto.createdAt),
            Instant.ofEpochMilli(dto.updatedAt)
        );
    }

    /** Flat file wrapper holding every persisted record. */
    private static final class FileDto {
        private List<RecordDto> records;
    }

    /** Flat Gson-friendly projection of an {@link EternalResponseRecord}. */
    private static final class RecordDto {
        private String responseId;
        private String messageId;
        private String channelId;
        private String guildId;
        private String userId;
        private String builderKey;
        private String payload;
        private String currentPageId;
        private int currentItemPage;
        private List<String> pageHistory;
        private long createdAt;
        private long updatedAt;
    }

}
