package dev.simplified.discordapi.feature.extractor.ui;

import dev.simplified.dataflow.DataType;
import dev.simplified.dataflow.stage.FieldSpec;
import dev.simplified.dataflow.stage.Stage;
import dev.simplified.dataflow.stage.StageConfig;
import dev.simplified.dataflow.stage.meta.StageReflection;
import dev.simplified.dataflow.stage.meta.StageSpec;
import dev.simplified.discordapi.component.interaction.Modal;
import dev.simplified.discordapi.component.interaction.TextInput;
import dev.simplified.discordapi.component.layout.Label;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the configuration schema of a {@link Stage} implementation class as a {@link Modal}
 * of {@link Label}-wrapped {@link TextInput}s. Submitted values are routed back through
 * {@link StageReflection#of(Class)}'s {@code fromConfig} to reconstruct a fresh stage.
 * <p>
 * Wire format: each input's identifier matches the {@link FieldSpec#name()}, so handlers can
 * pull the submitted text by field name without bookkeeping. Stages with no schema yield an
 * empty modal body - they are still added/edited via this modal so the "Add Stage" flow
 * always lands on a confirm-or-cancel surface.
 * <p>
 * Sub-pipeline ({@link FieldSpec.Type#SUB_PIPELINE} / {@link FieldSpec.Type#SUB_PIPELINES_MAP} /
 * {@link FieldSpec.Type#TYPED_SUB_PIPELINES_MAP}) fields are skipped; stages with sub-pipelines
 * use a dedicated drill-down flow added in step 2.7.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StageEditModal {

    /** Component id prefix; suffix is {@code add.<id>} for new stages or {@code edit.<index>} for existing. */
    public static final @NotNull String ID_PREFIX = "extractor.builder.modal.";

    /** Identifier prefix for an "add new stage" submission, suffix is the stage's {@link StageSpec#id()}. */
    public static final @NotNull String ID_ADD_PREFIX = ID_PREFIX + "add.";

    /** Identifier prefix for an "edit existing stage" submission, suffix is the stage index. */
    public static final @NotNull String ID_EDIT_PREFIX = ID_PREFIX + "edit.";

    /**
     * Builds an "add stage" modal for the given stage class, with all inputs blank.
     *
     * @param stageClass the stage implementation class being added
     * @return the modal
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static @NotNull Modal forAdd(@NotNull Class<? extends Stage> stageClass) {
        Class<? extends Stage<?, ?>> typed = (Class<? extends Stage<?, ?>>) stageClass;
        StageSpec spec = typed.getAnnotation(StageSpec.class);
        return build(typed, null, ID_ADD_PREFIX + spec.id(), "Add " + spec.displayName());
    }

    /**
     * Builds an "edit stage" modal pre-filled from {@code current}.
     *
     * @param index the zero-based stage index whose value is being edited
     * @param stageClass the stage implementation class
     * @param current the current configuration to pre-fill
     * @return the modal
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static @NotNull Modal forEdit(int index, @NotNull Class<? extends Stage> stageClass, @NotNull StageConfig current) {
        Class<? extends Stage<?, ?>> typed = (Class<? extends Stage<?, ?>>) stageClass;
        StageSpec spec = typed.getAnnotation(StageSpec.class);
        return build(typed, current, ID_EDIT_PREFIX + index, "Edit " + spec.displayName() + " #" + index);
    }

    private static @NotNull Modal build(@NotNull Class<? extends Stage<?, ?>> stageClass, @Nullable StageConfig prefill, @NotNull String identifier, @NotNull String title) {
        Modal.Builder modal = Modal.builder()
            .withIdentifier(identifier)
            .withTitle(title);

        for (FieldSpec<?> spec : StageReflection.of(stageClass).schema()) {
            if (isSubPipeline(spec.type())) continue;

            TextInput input = TextInput.builder()
                .withIdentifier(spec.name())
                .withPlaceholder(spec.placeholder())
                .withValue(prefill == null ? null : renderValue(prefill.raw(spec.name()), spec.type()))
                .build();

            Label label = Label.builder()
                .withTitle(spec.label())
                .withComponent(input)
                .build();

            modal.withComponents(label);
        }

        return modal.build();
    }

    private static boolean isSubPipeline(@NotNull FieldSpec.Type type) {
        return type == FieldSpec.Type.SUB_PIPELINE
            || type == FieldSpec.Type.SUB_PIPELINES_MAP
            || type == FieldSpec.Type.TYPED_SUB_PIPELINES_MAP;
    }

    private static @Nullable String renderValue(@Nullable Object value, @NotNull FieldSpec.Type type) {
        if (value == null) return null;
        return type == FieldSpec.Type.DATA_TYPE ? ((DataType<?>) value).label() : String.valueOf(value);
    }

}
