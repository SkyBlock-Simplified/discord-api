package dev.simplified.discordapi.feature.extractor;

import dev.simplified.dataflow.DataPipeline;
import dev.simplified.dataflow.PipelineContext;
import dev.simplified.dataflow.ValidationReport;
import dev.simplified.discordapi.feature.extractor.ui.ExtractorResultFormatter;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * Pure-logic runner for the {@code /extract <shortId>} flow. Takes an {@link ExtractorStore}
 * plus the calling user/guild, looks up a saved {@link Extractor} with visibility enforced,
 * builds a {@link PipelineContext} via the supplied factory (so callers can attach a
 * resolver, http client, or context bag entries), and executes the pipeline.
 * <p>
 * The result is a {@link Result} carrying either the runtime value or a banner-friendly
 * error message - never an exception. The future {@code /extract} slash command consumes
 * this directly: read the option, call {@link #run}, format the {@link Result} for Discord.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExtractorRunner {

    /**
     * Outcome of an extract-and-run attempt.
     *
     * @param extractor the resolved extractor, {@code null} when not found
     * @param value the runtime result, {@code null} on failure or when the pipeline returns null
     * @param error a banner-friendly error message, {@code null} on success
     */
    public record Result(@Nullable Extractor extractor, @Nullable Object value, @Nullable String error) {

        /** {@code true} when the runner succeeded and produced a value (which may be {@code null}). */
        public boolean ok() {
            return this.error == null && this.extractor != null;
        }

        /**
         * Renders the outcome as a Discord-friendly multi-line string ready to paste into a
         * message body or section.
         *
         * @return the formatted message
         */
        public @NotNull String formatted() {
            if (this.error != null) return this.error;
            if (this.extractor == null) return "Not found";
            return ExtractorResultFormatter.format(this.value);
        }

    }

    /**
     * Runs the extractor with the given {@code shortId}, visibility-checked against the caller.
     *
     * @param store the extractor store to look up against
     * @param shortId the {@code /extract} lookup key
     * @param callerUserId the calling user's snowflake
     * @param callerGuildId the calling user's guild snowflake, or {@code null} in DM
     * @param contextFactory builds the {@link PipelineContext} fresh per run; receives the
     *                       resolved extractor so callers can attach caller-bag entries
     * @return a Mono of the run result
     */
    public static @NotNull Mono<Result> run(
        @NotNull ExtractorStore store,
        @NotNull String shortId,
        long callerUserId,
        @Nullable Long callerGuildId,
        @NotNull Function<Extractor, PipelineContext> contextFactory
    ) {
        return store.findByShortId(shortId, callerUserId, callerGuildId)
            .map(extractor -> executeOne(extractor, contextFactory))
            .defaultIfEmpty(new Result(null, null, "No extractor named '" + shortId + "' visible to you"));
    }

    private static @NotNull Result executeOne(@NotNull Extractor extractor, @NotNull Function<Extractor, PipelineContext> contextFactory) {
        DataPipeline<?> pipeline;
        try {
            pipeline = extractor.pipeline();
        } catch (RuntimeException ex) {
            return new Result(extractor, null, "Stored definition is unparseable: " + ex.getMessage());
        }
        ValidationReport report = pipeline.validate();
        if (!report.isValid())
            return new Result(extractor, null,
                "Stored pipeline has " + report.issues().size() + " validation issue(s)");
        try {
            Object value = pipeline.execute(contextFactory.apply(extractor));
            return new Result(extractor, value, null);
        } catch (RuntimeException ex) {
            return new Result(extractor, null, "Run failed: " + ex.getMessage());
        }
    }

}
