package dev.simplified.discordapi.feature.extractor.ui;

import dev.simplified.dataflow.DataPipeline;
import dev.simplified.dataflow.DataType;
import dev.simplified.dataflow.PipelineContext;
import dev.simplified.dataflow.ValidationReport;
import dev.simplified.dataflow.chain.Chain;
import dev.simplified.dataflow.chain.NamedChains;
import dev.simplified.dataflow.stage.FieldSpec;
import dev.simplified.dataflow.stage.SourceStage;
import dev.simplified.dataflow.stage.Stage;
import dev.simplified.dataflow.stage.source.EmbedSource;
import dev.simplified.dataflow.stage.terminal.collect.MapCollect;
import dev.simplified.discordapi.feature.extractor.Extractor;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.NamingStyle;
import dev.simplified.annotations.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Owns the mutable {@link PipelineBuilderState} for a single editor session and exposes pure
 * mutation methods that the Discord interaction handlers wire into.
 * <p>
 * Each interaction (category select, kind select, modal submit, edit accessory click) is one
 * call into this session; the session updates state atomically and returns a new
 * {@code PipelineBuilderState} that the caller hands to {@link PipelineBuilderPage} for
 * re-rendering. The session itself does not touch Discord4J - it is testable in plain JUnit.
 *
 * @see PipelineBuilderPage
 * @see StageConfigParser
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter(style = NamingStyle.FLUENT)
public final class PipelineBuilderSession {

    private final @NotNull AtomicReference<PipelineBuilderState> stateRef;

    /**
     * Starts a session for a fresh, unsaved pipeline.
     *
     * @return a new session
     */
    public static @NotNull PipelineBuilderSession startNew() {
        return new PipelineBuilderSession(new AtomicReference<>(PipelineBuilderState.empty()));
    }

    /**
     * Starts a session at the given initial state, typically loaded from a saved row.
     *
     * @param initial the initial state
     * @return a new session
     */
    public static @NotNull PipelineBuilderSession resume(@NotNull PipelineBuilderState initial) {
        return new PipelineBuilderSession(new AtomicReference<>(initial));
    }

    /**
     * Returns the current builder state.
     *
     * @return the current state
     */
    public @NotNull PipelineBuilderState state() {
        return this.stateRef.get();
    }

    /**
     * Appends a stage to the end of the pipeline by parsing modal submission values for
     * {@code stageClass}. On parse error or invalid chain the state's banner is set and the
     * pipeline is unchanged.
     *
     * @param stageClass the stage implementation class being added
     * @param values the submitted modal values keyed by {@link FieldSpec#name() field name}
     * @return the new state after the mutation attempt
     */
    public @NotNull PipelineBuilderState appendStage(@NotNull Class<? extends Stage> stageClass, @NotNull Map<String, String> values) {
        StageConfigParser.StageResult result = StageConfigParser.parseAndBuild(stageClass, values);
        if (!result.ok())
            return this.stateRef.updateAndGet(s -> s.toBuilder().banner(result.error()).build());
        return this.stateRef.updateAndGet(s -> {
            try {
                return s.toBuilder()
                    .pipeline(rebuildAppending(s.pipeline(), result.stage()))
                    .banner(null)
                    .latestResult(null)
                    .build();
            } catch (RuntimeException ex) {
                return s.toBuilder().banner("Cannot append stage: " + ex.getMessage()).build();
            }
        });
    }

    /**
     * Replaces the stage at {@code index} with one parsed from {@code values}. On parse error
     * the state's banner is set and the pipeline is unchanged.
     *
     * @param index zero-based stage index to replace
     * @param stageClass the stage implementation class
     * @param values the submitted modal values
     * @return the new state after the mutation attempt
     */
    public @NotNull PipelineBuilderState replaceStage(int index, @NotNull Class<? extends Stage> stageClass, @NotNull Map<String, String> values) {
        StageConfigParser.StageResult result = StageConfigParser.parseAndBuild(stageClass, values);
        if (!result.ok())
            return this.stateRef.updateAndGet(s -> s.toBuilder().banner(result.error()).build());
        return this.stateRef.updateAndGet(s -> {
            DataPipeline<?> current = s.pipeline();
            if (index < 0 || index >= current.stages().size())
                return s.toBuilder().banner("No such stage #" + index).build();
            try {
                return s.toBuilder()
                    .pipeline(rebuildReplacing(current, index, result.stage()))
                    .banner(null)
                    .latestResult(null)
                    .build();
            } catch (RuntimeException ex) {
                return s.toBuilder().banner("Cannot replace stage: " + ex.getMessage()).build();
            }
        });
    }

    /**
     * Removes the stage at {@code index}.
     *
     * @param index zero-based stage index to remove
     * @return the new state after the removal attempt
     */
    public @NotNull PipelineBuilderState removeStage(int index) {
        return this.stateRef.updateAndGet(s -> {
            DataPipeline<?> current = s.pipeline();
            if (index < 0 || index >= current.stages().size())
                return s.toBuilder().banner("No such stage #" + index).build();
            try {
                return s.toBuilder()
                    .pipeline(rebuildOmitting(current, index))
                    .banner(null)
                    .latestResult(null)
                    .build();
            } catch (RuntimeException ex) {
                return s.toBuilder().banner("Cannot remove stage: " + ex.getMessage()).build();
            }
        });
    }

    /**
     * Resets the pipeline to empty, preserving the label/shortId so the user can re-author.
     *
     * @return the reset state
     */
    public @NotNull PipelineBuilderState reset() {
        return this.stateRef.updateAndGet(s -> s.toBuilder()
            .pipeline(DataPipeline.empty())
            .banner(null)
            .latestResult(null)
            .build());
    }

    /**
     * Records a banner message - used by interaction handlers to surface validation errors.
     *
     * @param banner the banner text, or {@code null} to clear
     * @return the updated state
     */
    public @NotNull PipelineBuilderState banner(@Nullable String banner) {
        return this.stateRef.updateAndGet(s -> s.toBuilder().banner(banner).build());
    }

    /**
     * Records the latest run result - cleared whenever the pipeline is mutated.
     *
     * @param result the run result
     * @return the updated state
     */
    public @NotNull PipelineBuilderState recordRunResult(@Nullable Object result) {
        return this.stateRef.updateAndGet(s -> s.toBuilder()
            .latestResult(result)
            .banner(null)
            .build());
    }

    /**
     * Records a successful save: updates the state's label/shortId/backingRow to mirror the
     * persisted {@link Extractor} and posts a confirmation banner.
     *
     * @param saved the freshly-persisted extractor row
     * @return the updated state
     */
    public @NotNull PipelineBuilderState recordSaved(@NotNull Extractor saved) {
        return this.stateRef.updateAndGet(s -> s.toBuilder()
            .label(saved.getLabel())
            .shortId(saved.getShortId())
            .backingRow(saved)
            .banner("Saved as `" + saved.getShortId() + "`")
            .build());
    }

    /**
     * Executes the pipeline against {@code ctx}.
     * <p>
     * Validation issues are written to the banner (run is refused). Runtime exceptions are
     * caught and surfaced through the banner; the previous {@link PipelineBuilderState#latestResult()}
     * is left intact so users can compare against the last good run. Successful runs replace
     * {@code latestResult} and clear any banner.
     *
     * @param ctx the pipeline context
     * @return the new state after the run attempt
     */
    public @NotNull PipelineBuilderState runPipeline(@NotNull PipelineContext ctx) {
        DataPipeline<?> pipeline = this.stateRef.get().pipeline();
        ValidationReport report = pipeline.validate();
        if (!report.isValid())
            return this.stateRef.updateAndGet(s -> s.toBuilder()
                .banner("Cannot run: " + report.issues().size() + " validation issue(s)")
                .build());
        try {
            Object result = pipeline.execute(ctx);
            return this.stateRef.updateAndGet(s -> s.toBuilder()
                .latestResult(result)
                .banner(null)
                .build());
        } catch (RuntimeException ex) {
            return this.stateRef.updateAndGet(s -> s.toBuilder()
                .banner("Run failed: " + ex.getMessage())
                .build());
        }
    }

    /**
     * Validation outcome of a "Save as..." submission.
     *
     * @param label the trimmed display label
     * @param shortId the trimmed short id used as the {@code /extract} lookup key
     * @param visibility the parsed visibility scope
     * @param error a banner-friendly error message, {@code null} when {@link #ok()}
     */
    public record SaveValidation(
        @Nullable String label,
        @Nullable String shortId,
        @Nullable Extractor.Visibility visibility,
        @Nullable String error
    ) {

        /** {@code true} when validation succeeded. */
        public boolean ok() {
            return this.error == null;
        }

    }

    private static final @NotNull Pattern SHORT_ID_RE = Pattern.compile("^[a-z0-9_]{1,32}$");

    /**
     * Validates the inputs from the save modal. Pure - no I/O. Caller persists the row.
     *
     * @param values the submitted values keyed by {@link SaveExtractorModal} field id
     * @return the validation result
     */
    public @NotNull SaveValidation validateSaveInputs(@NotNull Map<String, String> values) {
        if (this.stateRef.get().pipeline().stages().isEmpty())
            return new SaveValidation(null, null, null, "Cannot save an empty pipeline");

        ValidationReport report = this.stateRef.get().pipeline().validate();
        if (!report.isValid())
            return new SaveValidation(null, null, null,
                "Cannot save: " + report.issues().size() + " validation issue(s)");

        String label = trimOrEmpty(values.get(SaveExtractorModal.FIELD_LABEL));
        String shortId = trimOrEmpty(values.get(SaveExtractorModal.FIELD_SHORT_ID)).toLowerCase();
        String visibilityRaw = trimOrEmpty(values.get(SaveExtractorModal.FIELD_VISIBILITY)).toUpperCase();

        if (label.isEmpty())
            return new SaveValidation(null, null, null, "Label is required");
        if (label.length() > 64)
            return new SaveValidation(null, null, null, "Label must be 64 characters or fewer");
        if (shortId.isEmpty())
            return new SaveValidation(null, null, null, "Short id is required");
        if (!SHORT_ID_RE.matcher(shortId).matches())
            return new SaveValidation(null, null, null,
                "Short id must be 1-32 lowercase letters, digits, or underscores");
        Extractor.Visibility visibility;
        try {
            visibility = Extractor.Visibility.valueOf(visibilityRaw);
        } catch (IllegalArgumentException ex) {
            return new SaveValidation(null, null, null,
                "Visibility must be PRIVATE, GUILD, or PUBLIC");
        }
        return new SaveValidation(label, shortId, visibility, null);
    }

    /**
     * Convenience wrapper around {@link #validateSaveInputs(Map)} that surfaces a banner on
     * failure and otherwise returns the validation untouched. Caller still does the persist.
     *
     * @param values the submitted values
     * @return the validation result
     */
    public @NotNull SaveValidation validateAndStoreBanner(@NotNull Map<String, String> values) {
        SaveValidation v = validateSaveInputs(values);
        if (!v.ok()) banner(v.error());
        return v;
    }

    private static @NotNull String trimOrEmpty(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Appends an {@link EmbedSource} stage referencing the saved {@code extractor}. The
     * embedded id is the extractor's {@link Extractor#getId() UUID}; the embed's declared
     * output type is read from the extractor's terminal stage. Validation issues in the
     * stored pipeline are surfaced to the banner without mutating the outer pipeline.
     *
     * @param extractor the saved extractor to embed
     * @return the new state after the mutation attempt
     */
    public @NotNull PipelineBuilderState appendEmbedStage(@NotNull Extractor extractor) {
        DataPipeline<?> inner;
        try {
            inner = extractor.pipeline();
        } catch (RuntimeException ex) {
            return banner("Embedded extractor has an unparseable definition");
        }
        if (inner.stages().isEmpty())
            return banner("Cannot embed an empty extractor");
        @SuppressWarnings("unchecked")
        DataType<Object> outputType = (DataType<Object>) inner.stages().getLast().outputType();
        Stage<?, ?> embed = EmbedSource.of(extractor.getId().toString(), outputType);
        return this.stateRef.updateAndGet(s -> {
            try {
                return s.toBuilder()
                    .pipeline(rebuildAppending(s.pipeline(), embed))
                    .banner(null)
                    .latestResult(null)
                    .build();
            } catch (RuntimeException ex) {
                return s.toBuilder().banner("Cannot append embed: " + ex.getMessage()).build();
            }
        });
    }

    /* ====================  branch sub-chain mutation  ==================== */

    /**
     * Adds an empty named output to the {@link MapCollect} stage at {@code branchIndex}.
     *
     * @param branchIndex top-level pipeline index of the {@code MapCollect} stage
     * @param outputName the new output name (must not already exist)
     * @return the new state after the mutation attempt
     */
    public @NotNull PipelineBuilderState addBranchOutput(int branchIndex, @NotNull String outputName) {
        return mutateBranch(branchIndex, branch -> {
            if (branch.outputs().chains().containsKey(outputName))
                return new BranchMutation(null, "Output '" + outputName + "' already exists");
            LinkedHashMap<String, List<Stage<?, ?>>> next = copyOutputs(branch.outputs());
            next.put(outputName, new java.util.ArrayList<>());
            return new BranchMutation(rebuildBranch(branch.inputType(), next), null);
        });
    }

    /**
     * Removes a named output from the {@link MapCollect} stage at {@code branchIndex}.
     *
     * @param branchIndex top-level pipeline index of the {@code MapCollect} stage
     * @param outputName the output to remove
     * @return the new state after the mutation attempt
     */
    public @NotNull PipelineBuilderState removeBranchOutput(int branchIndex, @NotNull String outputName) {
        return mutateBranch(branchIndex, branch -> {
            if (!branch.outputs().chains().containsKey(outputName))
                return new BranchMutation(null, "No such output '" + outputName + "'");
            LinkedHashMap<String, List<Stage<?, ?>>> next = copyOutputs(branch.outputs());
            next.remove(outputName);
            return new BranchMutation(rebuildBranch(branch.inputType(), next), null);
        });
    }

    /**
     * Appends a stage to the named sub-chain of a {@link MapCollect} stage. Enforces the v1
     * depth-1 cap by rejecting nested {@link MapCollect} stages.
     *
     * @param branchIndex top-level pipeline index of the {@code MapCollect} stage
     * @param outputName the named sub-chain to append into
     * @param stageClass the stage implementation class being added
     * @param values the submitted modal values
     * @return the new state after the mutation attempt
     */
    public @NotNull PipelineBuilderState appendBranchStage(int branchIndex, @NotNull String outputName, @NotNull Class<? extends Stage> stageClass, @NotNull Map<String, String> values) {
        if (stageClass.equals(MapCollect.class))
            return banner("Branch stages cannot be nested inside another branch");
        StageConfigParser.StageResult result = StageConfigParser.parseAndBuild(stageClass, values);
        if (!result.ok()) return banner(result.error());
        return mutateBranch(branchIndex, branch -> {
            if (!branch.outputs().chains().containsKey(outputName))
                return new BranchMutation(null, "No such output '" + outputName + "'");
            LinkedHashMap<String, List<Stage<?, ?>>> next = copyOutputs(branch.outputs());
            next.get(outputName).add(result.stage());
            return new BranchMutation(rebuildBranch(branch.inputType(), next), null);
        });
    }

    /**
     * Replaces a stage at {@code stageIndex} inside a named sub-chain.
     *
     * @param branchIndex top-level pipeline index of the {@code MapCollect} stage
     * @param outputName the named sub-chain
     * @param stageIndex zero-based index of the stage within the sub-chain
     * @param stageClass the new stage implementation class
     * @param values the submitted modal values
     * @return the new state after the mutation attempt
     */
    public @NotNull PipelineBuilderState replaceBranchStage(int branchIndex, @NotNull String outputName, int stageIndex, @NotNull Class<? extends Stage> stageClass, @NotNull Map<String, String> values) {
        if (stageClass.equals(MapCollect.class))
            return banner("Branch stages cannot be nested inside another branch");
        StageConfigParser.StageResult result = StageConfigParser.parseAndBuild(stageClass, values);
        if (!result.ok()) return banner(result.error());
        return mutateBranch(branchIndex, branch -> {
            if (!branch.outputs().chains().containsKey(outputName))
                return new BranchMutation(null, "No such output '" + outputName + "'");
            List<Stage<?, ?>> chain = new java.util.ArrayList<>(branch.outputs().chains().get(outputName).stages());
            if (stageIndex < 0 || stageIndex >= chain.size())
                return new BranchMutation(null, "No such stage #" + stageIndex + " in '" + outputName + "'");
            chain.set(stageIndex, result.stage());
            LinkedHashMap<String, List<Stage<?, ?>>> next = copyOutputs(branch.outputs());
            next.put(outputName, chain);
            return new BranchMutation(rebuildBranch(branch.inputType(), next), null);
        });
    }

    /**
     * Removes a stage at {@code stageIndex} from a named sub-chain.
     *
     * @param branchIndex top-level pipeline index of the {@code MapCollect} stage
     * @param outputName the named sub-chain
     * @param stageIndex zero-based index of the stage within the sub-chain
     * @return the new state after the mutation attempt
     */
    public @NotNull PipelineBuilderState removeBranchStage(int branchIndex, @NotNull String outputName, int stageIndex) {
        return mutateBranch(branchIndex, branch -> {
            if (!branch.outputs().chains().containsKey(outputName))
                return new BranchMutation(null, "No such output '" + outputName + "'");
            List<Stage<?, ?>> chain = new java.util.ArrayList<>(branch.outputs().chains().get(outputName).stages());
            if (stageIndex < 0 || stageIndex >= chain.size())
                return new BranchMutation(null, "No such stage #" + stageIndex + " in '" + outputName + "'");
            chain.remove(stageIndex);
            LinkedHashMap<String, List<Stage<?, ?>>> next = copyOutputs(branch.outputs());
            next.put(outputName, chain);
            return new BranchMutation(rebuildBranch(branch.inputType(), next), null);
        });
    }

    /* ====================  internals  ==================== */

    /** Result of an attempted branch mutation: either the new {@link MapCollect} or an error. */
    private record BranchMutation(@Nullable MapCollect<?> branch, @Nullable String error) {}

    private @NotNull PipelineBuilderState mutateBranch(int branchIndex, @NotNull java.util.function.Function<MapCollect<?>, BranchMutation> mutator) {
        return this.stateRef.updateAndGet(s -> {
            DataPipeline<?> current = s.pipeline();
            if (branchIndex < 0 || branchIndex >= current.stages().size())
                return s.toBuilder().banner("No such stage #" + branchIndex).build();
            Stage<?, ?> stage = current.stages().get(branchIndex);
            if (!(stage instanceof MapCollect<?> branch))
                return s.toBuilder().banner("Stage #" + branchIndex + " is not a Branch").build();
            BranchMutation result = mutator.apply(branch);
            if (result.error() != null)
                return s.toBuilder().banner(result.error()).build();
            try {
                return s.toBuilder()
                    .pipeline(rebuildReplacing(current, branchIndex, result.branch()))
                    .banner(null)
                    .latestResult(null)
                    .build();
            } catch (RuntimeException ex) {
                return s.toBuilder().banner("Cannot mutate branch: " + ex.getMessage()).build();
            }
        });
    }

    private static @NotNull LinkedHashMap<String, List<Stage<?, ?>>> copyOutputs(@NotNull NamedChains<?> outputs) {
        LinkedHashMap<String, List<Stage<?, ?>>> copy = new LinkedHashMap<>();
        for (var entry : outputs.chains().entrySet())
            copy.put(entry.getKey(), new java.util.ArrayList<>(entry.getValue().stages()));
        return copy;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static @NotNull MapCollect<?> rebuildBranch(@NotNull DataType<?> inputType, @NotNull LinkedHashMap<String, List<Stage<?, ?>>> outputs) {
        MapCollect.Builder<?> b = MapCollect.over((DataType) inputType);
        for (Map.Entry<String, List<Stage<?, ?>>> entry : outputs.entrySet()) {
            List<Stage<?, ?>> stages = entry.getValue();
            b.output(entry.getKey(), chain -> {
                for (Stage<?, ?> s : stages) ((dev.simplified.dataflow.chain.ChainBuilder) chain).stage(s);
            });
        }
        return b.build();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static @NotNull DataPipeline<?> rebuildAppending(@NotNull DataPipeline<?> current, @NotNull Stage<?, ?> appended) {
        DataPipeline.Origin sb = DataPipeline.builder();
        DataPipeline.Builder b = null;
        for (Stage<?, ?> s : current.stages()) {
            if (b == null) b = sb.source((SourceStage<?>) s);
            else b = b.stage(s);
        }
        if (b == null) b = sb.source((SourceStage<?>) appended);
        else b = b.stage(appended);
        return b.build();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static @NotNull DataPipeline<?> rebuildReplacing(@NotNull DataPipeline<?> current, int index, @NotNull Stage<?, ?> replacement) {
        DataPipeline.Origin sb = DataPipeline.builder();
        DataPipeline.Builder b = null;
        for (int i = 0; i < current.stages().size(); i++) {
            Stage<?, ?> s = i == index ? replacement : current.stages().get(i);
            if (i == 0) b = sb.source((SourceStage<?>) s);
            else b = b.stage(s);
        }
        return b.build();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static @NotNull DataPipeline<?> rebuildOmitting(@NotNull DataPipeline<?> current, int index) {
        DataPipeline.Origin sb = DataPipeline.builder();
        DataPipeline.Builder b = null;
        for (int i = 0; i < current.stages().size(); i++) {
            if (i == index) continue;
            Stage<?, ?> s = current.stages().get(i);
            if (b == null) b = sb.source((SourceStage<?>) s);
            else b = b.stage(s);
        }
        return b.build();
    }

}
