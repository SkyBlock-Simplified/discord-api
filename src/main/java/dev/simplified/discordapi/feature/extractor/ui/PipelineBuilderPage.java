package dev.simplified.discordapi.feature.extractor.ui;

import dev.simplified.dataflow.DataPipeline;
import dev.simplified.dataflow.ValidationReport;
import dev.simplified.dataflow.stage.Stage;
import dev.simplified.discordapi.component.TextDisplay;
import dev.simplified.discordapi.component.interaction.Button;
import dev.simplified.discordapi.component.layout.ActionRow;
import dev.simplified.discordapi.component.layout.Container;
import dev.simplified.discordapi.component.layout.Section;
import dev.simplified.discordapi.component.layout.Separator;
import dev.simplified.discordapi.component.scope.ContainerComponent;
import dev.simplified.discordapi.context.component.ButtonContext;
import dev.simplified.discordapi.response.page.Page;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * Renders a {@link PipelineBuilderState} into a {@link Page}.
 * <p>
 * Layout:
 * <ul>
 *     <li>Title {@link TextDisplay} with the label or {@code (unsaved)}</li>
 *     <li>One {@link Section} per stage, accessory = an {@code Edit} button</li>
 *     <li>{@link Separator} divider</li>
 *     <li>Live-preview {@link Section} showing validation status or last result</li>
 *     <li>Footer {@link ActionRow}: {@code Add Stage}, {@code Save as...}, {@code Reset},
 *     {@code Close}; the preview section's accessory carries {@code Run}</li>
 * </ul>
 * <p>
 * Two render modes:
 * <ul>
 *     <li>{@link #of(PipelineBuilderState)} - read-only baseline; every interactive component
 *     is rendered without an {@link Button#onInteract} handler. Used by render-only tests.</li>
 *     <li>{@link #of(PipelineBuilderState, Handlers)} - live mode; handlers are bound inline
 *     for the live builder Response. Used by {@link PipelineBuilderResponse}.</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PipelineBuilderPage {

    /** Component identifier for the per-stage edit button. Suffix is the stage index. */
    public static final @NotNull String ID_EDIT_STAGE_PREFIX = "extractor.builder.edit-stage.";

    /** Component identifier for the {@code Add Stage} footer button. */
    public static final @NotNull String ID_ADD_STAGE = "extractor.builder.add-stage";

    /** Component identifier for the {@code Run} preview-section accessory. */
    public static final @NotNull String ID_RUN = "extractor.builder.run";

    /** Component identifier for the {@code Save as...} footer button. */
    public static final @NotNull String ID_SAVE = "extractor.builder.save";

    /** Component identifier for the {@code Reset} footer button. */
    public static final @NotNull String ID_RESET = "extractor.builder.reset";

    /** Component identifier for the {@code Close} footer button. */
    public static final @NotNull String ID_CLOSE = "extractor.builder.close";

    /**
     * Bundle of interaction handlers wired into the page's buttons.
     *
     * @param onAddStage handler fired when the {@code Add Stage} button is clicked
     * @param onEditStage handler fired when a per-stage {@code Edit} accessory is clicked;
     *                    the int parameter is the zero-based stage index
     * @param onRun handler fired when the preview {@code Run} accessory is clicked
     * @param onSave handler fired when the footer {@code Save as...} button is clicked
     * @param onReset handler fired when the footer {@code Reset} button is clicked
     * @param onClose handler fired when the footer {@code Close} button is clicked
     */
    public record Handlers(
        @Nullable Function<ButtonContext, Mono<Void>> onAddStage,
        @Nullable IndexedHandler onEditStage,
        @Nullable Function<ButtonContext, Mono<Void>> onRun,
        @Nullable Function<ButtonContext, Mono<Void>> onSave,
        @Nullable Function<ButtonContext, Mono<Void>> onReset,
        @Nullable Function<ButtonContext, Mono<Void>> onClose
    ) {

        /** Returns a Handlers bundle with every callback set to {@code null} (no-op). */
        public static @NotNull Handlers noop() {
            return new Handlers(null, null, null, null, null, null);
        }

    }

    /** Stage-index-aware button handler. */
    @FunctionalInterface
    public interface IndexedHandler {
        @NotNull Mono<Void> handle(int stageIndex, @NotNull ButtonContext ctx);
    }

    /**
     * Builds a read-only page from {@code state}, with no interaction handlers.
     *
     * @param state the builder state
     * @return the rendered page
     */
    public static @NotNull Page of(@NotNull PipelineBuilderState state) {
        return of(state, Handlers.noop());
    }

    /**
     * Builds a live page from {@code state} with handlers bound to its interactive components.
     *
     * @param state the builder state
     * @param handlers the interaction handlers
     * @return the rendered page
     */
    public static @NotNull Page of(@NotNull PipelineBuilderState state, @NotNull Handlers handlers) {
        return withCustomFooter(state, handlers.onEditStage(), handlers.onRun(), footerRow(handlers));
    }

    /**
     * Builds a page that shares the main view's title/stages/preview but lets the caller
     * supply its own footer components - used by {@link PipelineBuilderResponse} to render
     * picker pages (category select, kind select, embed select, etc.) without duplicating
     * the rest of the layout.
     *
     * @param state the builder state
     * @param onEditStage handler for per-stage Edit accessories, or {@code null}
     * @param onRun handler for the preview Run accessory, or {@code null}
     * @param footer container components appended after the preview section
     * @return the rendered page
     */
    public static @NotNull Page withCustomFooter(
        @NotNull PipelineBuilderState state,
        @Nullable IndexedHandler onEditStage,
        @Nullable Function<ButtonContext, Mono<Void>> onRun,
        @NotNull ContainerComponent... footer
    ) {
        Container.Builder container = Container.builder();
        container.withComponents(TextDisplay.of(titleLine(state)), Separator.small());

        DataPipeline<?> pipeline = state.pipeline();
        for (int i = 0; i < pipeline.stages().size(); i++) {
            Stage<?, ?> stage = pipeline.stages().get(i);
            container.withComponents(StageSection.of(i, stage, onEditStage));
        }

        container.withComponents(Separator.small());
        container.withComponents(previewSection(state, onRun));
        container.withComponents(Separator.small());
        for (ContainerComponent c : footer)
            container.withComponents(c);

        return Page.builder()
            .withComponents(container.build())
            .build();
    }

    private static @NotNull String titleLine(@NotNull PipelineBuilderState state) {
        String label = state.label().isEmpty() ? "(unsaved)" : state.label();
        StringBuilder out = new StringBuilder("# Pipeline Builder - ").append(label);
        if (!state.shortId().isEmpty())
            out.append(" `").append(state.shortId()).append("`");
        return out.toString();
    }

    private static @NotNull Section previewSection(@NotNull PipelineBuilderState state, @Nullable Function<ButtonContext, Mono<Void>> onRun) {
        StringBuilder body = new StringBuilder();
        if (state.banner() != null && !state.banner().isEmpty())
            body.append("[!] ").append(state.banner()).append("\n");

        ValidationReport report = state.pipeline().validate();
        if (!report.isValid()) {
            body.append("**Validation:** ").append(report.issues().size()).append(" issue(s)\n");
            for (ValidationReport.Issue issue : report.issues()) {
                String prefix = issue.stageIndex() < 0 ? "pipeline" : "stage #" + issue.stageIndex();
                body.append("- ").append(prefix).append(": ").append(issue.message()).append("\n");
            }
        } else if (state.latestResult() != null) {
            body.append("**Result:** `").append(formatResult(state.latestResult())).append("`");
        } else {
            body.append("Ready to run.");
        }

        if (body.isEmpty()) body.append("(empty)");

        Button.Builder accessory = Button.builder()
            .withStyle(Button.Style.PRIMARY)
            .withLabel(state.latestResult() != null ? "Re-run" : "Run")
            .withIdentifier(ID_RUN);
        if (onRun != null) accessory.onInteract(onRun);

        return Section.builder()
            .withAccessory(accessory.build())
            .withComponents(TextDisplay.of(body.toString()))
            .build();
    }

    private static @NotNull ActionRow footerRow(@NotNull Handlers handlers) {
        Button.Builder add = Button.builder()
            .withStyle(Button.Style.PRIMARY)
            .withLabel("Add Stage")
            .withIdentifier(ID_ADD_STAGE);
        if (handlers.onAddStage() != null) add.onInteract(handlers.onAddStage());

        Button.Builder save = Button.builder()
            .withStyle(Button.Style.SUCCESS)
            .withLabel("Save as...")
            .withIdentifier(ID_SAVE);
        if (handlers.onSave() != null) save.onInteract(handlers.onSave());

        Button.Builder reset = Button.builder()
            .withStyle(Button.Style.SECONDARY)
            .withLabel("Reset")
            .withIdentifier(ID_RESET);
        if (handlers.onReset() != null) reset.onInteract(handlers.onReset());

        Button.Builder close = Button.builder()
            .withStyle(Button.Style.DANGER)
            .withLabel("Close")
            .withIdentifier(ID_CLOSE);
        if (handlers.onClose() != null) close.onInteract(handlers.onClose());

        return ActionRow.of(add.build(), save.build(), reset.build(), close.build());
    }

    private static @NotNull String formatResult(@Nullable Object value) {
        // Preview Section uses inline backticks; show a single-line snapshot here, leaving
        // the multi-line shape-aware rendering to ExtractorResultFormatter for /extract.
        if (value == null) return "null";
        String s = String.valueOf(value).replace('\n', ' ');
        return s.length() > 200 ? s.substring(0, 197) + "..." : s;
    }

}
