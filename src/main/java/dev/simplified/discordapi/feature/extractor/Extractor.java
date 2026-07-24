package dev.simplified.discordapi.feature.extractor;

import dev.simplified.dataflow.DataPipeline;
import dev.simplified.dataflow.serde.PipelineGson;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Framework-side data class representing a saved extractor.
 * <p>
 * Persistence is delegated to an {@link ExtractorStore} implementation supplied by the
 * consumer; this class carries no JPA, JSON, or other ORM annotations. Consumers wiring
 * a database-backed store typically map this POJO to and from their own entity type.
 * <p>
 * The pipeline body is held as a JSON blob in {@link #getDefinitionJson()}, round-tripped
 * through {@link PipelineGson} via {@link #pipeline()} and {@link #setPipeline(DataPipeline)}.
 */
@Getter
@Setter
public final class Extractor {

    /** Stable {@link UUID} primary key. */
    private @NotNull UUID id = new UUID(0L, 0L);

    /** Discord user snowflake (raw long) of the user who saved this extractor. */
    private long ownerUserId;

    /**
     * User-supplied short identifier used as the {@code /extract <shortId>} lookup key.
     * Lowercase letters, digits, and underscores only; expected to be unique per owner.
     */
    private @NotNull String shortId = "";

    /** Human-readable display label shown in the builder UI. */
    private @NotNull String label = "";

    /**
     * {@link DataPipeline} definition serialised by {@link PipelineGson}. Stored as a
     * top-level JSON array of stage descriptors.
     */
    private @NotNull String definitionJson = "[]";

    /** Sharing scope. Defaults to {@link Visibility#PRIVATE}. */
    private @NotNull Visibility visibility = Visibility.PRIVATE;

    /**
     * Discord guild snowflake (raw long) where this extractor was saved. Required when
     * {@link #visibility} is {@link Visibility#GUILD}; ignored otherwise.
     */
    private @Nullable Long guildId;

    /** Timestamp at which this extractor was created. */
    private @NotNull Instant createdAt = Instant.EPOCH;

    /**
     * Deserialises {@link #definitionJson} into a runtime {@link DataPipeline}.
     *
     * @return the rebuilt pipeline
     */
    public @NotNull DataPipeline<?> pipeline() {
        return PipelineGson.fromJson(this.definitionJson);
    }

    /**
     * Serialises {@code pipeline} via {@link PipelineGson} into {@link #definitionJson}.
     *
     * @param pipeline the pipeline to persist
     */
    public void setPipeline(@NotNull DataPipeline<?> pipeline) {
        this.definitionJson = PipelineGson.toJson(pipeline);
    }

    /**
     * Returns whether the caller is permitted to load and use this extractor.
     *
     * @param callerUserId the calling user's snowflake
     * @param callerGuildId the calling user's guild snowflake, or {@code null} in DM
     * @return {@code true} when the caller may use this extractor
     */
    public boolean canBeUsedBy(long callerUserId, @Nullable Long callerGuildId) {
        return switch (this.visibility) {
            case PRIVATE -> this.ownerUserId == callerUserId;
            case GUILD -> this.guildId != null && this.guildId.equals(callerGuildId);
            case PUBLIC -> true;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Extractor that = (Extractor) o;
        return Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.id);
    }

    /** Sharing scope of a saved extractor. */
    public enum Visibility {

        /** Owner-only access. */
        PRIVATE,

        /** Anyone in the same guild as the saver. */
        GUILD,

        /** Anyone, anywhere. */
        PUBLIC

    }

}
