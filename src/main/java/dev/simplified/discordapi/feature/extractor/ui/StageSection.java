package dev.simplified.discordapi.feature.extractor.ui;

import dev.simplified.dataflow.stage.Stage;
import dev.simplified.dataflow.stage.meta.StageSpec;
import dev.simplified.discordapi.component.TextDisplay;
import dev.simplified.discordapi.component.interaction.Button;
import dev.simplified.discordapi.component.layout.Section;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Renders one {@link Stage} as a {@link Section}: a {@link TextDisplay} body showing the
 * stage's category icon, kind, summary, and type-arrow line, plus an {@code Edit} button
 * accessory wired to {@link PipelineBuilderPage#ID_EDIT_STAGE_PREFIX} {@code + index}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StageSection {

    /**
     * Builds a section for the given stage with a no-op edit button (read-only render).
     *
     * @param index zero-based stage index used to wire the edit button identifier
     * @param stage the stage to render
     * @return the section
     */
    public static @NotNull Section of(int index, @NotNull Stage<?, ?> stage) {
        return of(index, stage, null);
    }

    /**
     * Builds a section for the given stage, attaching {@code onEdit} as the accessory's
     * click handler when non-null.
     *
     * @param index zero-based stage index used to wire the edit button identifier
     * @param stage the stage to render
     * @param onEdit the edit-button click handler, or {@code null} for a no-op button
     * @return the section
     */
    public static @NotNull Section of(int index, @NotNull Stage<?, ?> stage, @Nullable PipelineBuilderPage.IndexedHandler onEdit) {
        Button.Builder accessory = Button.builder()
            .withStyle(Button.Style.SECONDARY)
            .withLabel("Edit")
            .withIdentifier(PipelineBuilderPage.ID_EDIT_STAGE_PREFIX + index);
        if (onEdit != null) accessory.onInteract(ctx -> onEdit.handle(index, ctx));

        String body = "**" + icon(stage) + " " + categoryName(stage) + " #" + index + "** - "
            + stage.summary() + "\n"
            + "`" + stage.inputType().label() + " -> " + stage.outputType().label() + "`";

        return Section.builder()
            .withAccessory(accessory.build())
            .withComponents(TextDisplay.of(body))
            .build();
    }

    private static @NotNull String icon(@NotNull Stage<?, ?> stage) {
        String id = stage.kindId();
        return switch (id) {
            case "SOURCE_URL", "SOURCE_LITERAL", "SOURCE_LITERAL_LIST" -> ":globe_with_meridians:";
            case "PARSE_HTML", "PARSE_XML", "PARSE_JSON" -> ":scroll:";
            case "COLLECT_MAP" -> ":fork_and_knife:";
            case "SOURCE_EMBED" -> ":link:";
            default -> {
                StageSpec spec = stage.getClass().getAnnotation(StageSpec.class);
                if (spec != null) {
                    yield switch (spec.category()) {
                        case TERMINAL_AVERAGE, TERMINAL_COLLECT, TERMINAL_MATCH, TERMINAL_MINMAX, TERMINAL_SUM -> ":inbox_tray:";
                        case FILTER_DOM, FILTER_JSON, FILTER_LIST, FILTER_NUMERIC, FILTER_STRING,
                             PREDICATE_COMMON, PREDICATE_DOM, PREDICATE_JSON, PREDICATE_NUMERIC, PREDICATE_STRING
                            -> ":mag:";
                        default -> ":gear:";
                    };
                }
                yield ":gear:";
            }
        };
    }

    private static @NotNull String categoryName(@NotNull Stage<?, ?> stage) {
        StageSpec spec = stage.getClass().getAnnotation(StageSpec.class);
        if (spec == null) return stage.kindId();
        return switch (spec.category()) {
            case SOURCE -> "Source";
            case FILTER_DOM, FILTER_JSON, FILTER_LIST, FILTER_NUMERIC, FILTER_STRING -> "Filter";
            case PREDICATE_COMMON, PREDICATE_DOM, PREDICATE_JSON, PREDICATE_NUMERIC, PREDICATE_STRING -> "Predicate";
            case TRANSFORM_DOM, TRANSFORM_ENCODING, TRANSFORM_JSON, TRANSFORM_LIST,
                 TRANSFORM_PRIMITIVE, TRANSFORM_STRING -> "Transform";
            case TERMINAL_AVERAGE, TERMINAL_COLLECT, TERMINAL_MATCH, TERMINAL_MINMAX, TERMINAL_SUM -> "Collect";
        };
    }

}
