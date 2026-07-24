package dev.simplified.discordapi.feature.extractor.ui;

import dev.simplified.dataflow.DataPipeline;
import dev.simplified.discordapi.feature.extractor.Extractor;
import dev.simplified.annotations.ClassBuilder;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.NamingStyle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Snapshot of in-progress builder state used to render a {@link PipelineBuilderPage}.
 * <p>
 * Stages 2.4 onward mutate a {@code PipelineBuilderState} closure inside the live builder
 * {@code Response}; each interaction recomputes a new state and re-renders the page.
 *
 * @param pipeline the live pipeline being built
 * @param label the human-readable label, blank for an unsaved pipeline
 * @param shortId the short id used for {@code /extract <shortId>}, blank for unsaved
 * @param backingRow the saved row this state edits, or {@code null} for a fresh extractor
 * @param latestResult the most recent {@link DataPipeline#execute Run} result, or {@code null}
 *                     when no run has happened in this session
 * @param banner an optional ephemeral notice displayed above the live preview, used for
 *               validation errors and rejected mutations; cleared on the next successful edit
 */
@Getter(style = NamingStyle.FLUENT)
@ClassBuilder(style = NamingStyle.LOMBOK)
public final class PipelineBuilderState {

    private final @NotNull DataPipeline<?> pipeline;
    private final @NotNull String label;
    private final @NotNull String shortId;
    private final @Nullable Extractor backingRow;
    private final @Nullable Object latestResult;
    private final @Nullable String banner;

    /**
     * Initial state for a brand-new, unsaved pipeline.
     *
     * @return an empty builder state
     */
    public static @NotNull PipelineBuilderState empty() {
        return PipelineBuilderState.builder()
            .pipeline(DataPipeline.empty())
            .label("")
            .shortId("")
            .build();
    }

    /**
     * Initial state for editing the given saved {@link Extractor}.
     *
     * @param row the row being edited
     * @return a state whose pipeline matches {@code row}'s definition
     */
    public static @NotNull PipelineBuilderState forEditing(@NotNull Extractor row) {
        return PipelineBuilderState.builder()
            .pipeline(row.pipeline())
            .label(row.getLabel())
            .shortId(row.getShortId())
            .backingRow(row)
            .build();
    }

}
