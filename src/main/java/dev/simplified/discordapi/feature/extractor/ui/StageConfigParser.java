package dev.simplified.discordapi.feature.extractor.ui;

import dev.simplified.dataflow.DataType;
import dev.simplified.dataflow.DataTypes;
import dev.simplified.dataflow.stage.FieldSpec;
import dev.simplified.dataflow.stage.Stage;
import dev.simplified.dataflow.stage.StageConfig;
import dev.simplified.dataflow.stage.meta.StageReflection;
import dev.simplified.discordapi.component.interaction.Modal;
import dev.simplified.discordapi.component.interaction.TextInput;
import dev.simplified.discordapi.component.layout.Label;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Bridge from a submitted {@link Modal} to a populated {@link StageConfig} ready for
 * {@link StageReflection#of(Class)}'s {@code fromConfig}.
 * <p>
 * Field text values are converted according to each {@link FieldSpec#type() FieldSpec.Type};
 * unparseable values fail fast with a {@link Result#error()} string suitable for surfacing in
 * the builder's banner.
 * <p>
 * Sub-pipeline fields ({@link FieldSpec.Type#SUB_PIPELINE} /
 * {@link FieldSpec.Type#SUB_PIPELINES_MAP} / {@link FieldSpec.Type#TYPED_SUB_PIPELINES_MAP})
 * are skipped here - branch sub-pipelines are authored in a dedicated drill-down flow
 * (step 2.7) and merged into the config separately.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StageConfigParser {

    /**
     * Outcome of a parse attempt: either a populated configuration ready for the factory,
     * or an error message to surface in the builder banner.
     *
     * @param config the parsed configuration, {@code null} on error
     * @param error the error message, {@code null} on success
     */
    public record Result(@Nullable StageConfig config, @Nullable String error) {

        /** {@code true} when the parse succeeded. */
        public boolean ok() {
            return this.error == null;
        }

    }

    /**
     * Extracts the user-submitted text values from {@code modal} keyed by input identifier.
     *
     * @param modal the submitted modal carrying populated values
     * @return a map of {@code identifier -> submitted value}; missing or blank values are absent
     */
    public static @NotNull Map<String, String> readValues(@NotNull Modal modal) {
        Map<String, String> values = new HashMap<>();
        for (var component : modal.getComponents()) {
            if (!(component instanceof Label label)) continue;
            if (!(label.getComponent() instanceof TextInput input)) continue;
            input.getValue().ifPresent(v -> values.put(input.getIdentifier(), v));
        }
        return values;
    }

    /**
     * Attempts to build a {@link StageConfig} for {@code stageClass} from {@code values},
     * returning either the populated config or an error message.
     *
     * @param stageClass the stage implementation class being configured
     * @param values the submitted text values keyed by {@link FieldSpec#name()}
     * @return the parse result
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static @NotNull Result parse(@NotNull Class<? extends Stage> stageClass, @NotNull Map<String, String> values) {
        Class<? extends Stage<?, ?>> typed = (Class<? extends Stage<?, ?>>) stageClass;
        return parseTyped(typed, values);
    }

    private static @NotNull Result parseTyped(@NotNull Class<? extends Stage<?, ?>> stageClass, @NotNull Map<String, String> values) {
        StageConfig.Builder b = StageConfig.builder();
        for (FieldSpec<?> spec : StageReflection.of(stageClass).schema()) {
            if (isSubPipeline(spec.type())) continue;
            String raw = values.get(spec.name());
            if (raw == null || raw.isEmpty()) {
                if (spec.type() == FieldSpec.Type.STRING) {
                    b.string(spec.name(), "");
                    continue;
                }
                return new Result(null, "Missing value for '" + spec.label() + "'");
            }
            try {
                applyValue(b, spec, raw);
            } catch (NumberFormatException ex) {
                return new Result(null, "Invalid number for '" + spec.label() + "': " + raw);
            } catch (IllegalArgumentException ex) {
                return new Result(null, "'" + spec.label() + "': " + ex.getMessage());
            }
        }
        return new Result(b.build(), null);
    }

    private static boolean isSubPipeline(@NotNull FieldSpec.Type type) {
        return type == FieldSpec.Type.SUB_PIPELINE
            || type == FieldSpec.Type.SUB_PIPELINES_MAP
            || type == FieldSpec.Type.TYPED_SUB_PIPELINES_MAP;
    }

    private static void applyValue(@NotNull StageConfig.Builder b, @NotNull FieldSpec<?> spec, @NotNull String raw) {
        switch (spec.type()) {
            case STRING -> b.string(spec.name(), raw);
            case INT -> b.integer(spec.name(), Integer.parseInt(raw.trim()));
            case LONG -> b.longVal(spec.name(), Long.parseLong(raw.trim()));
            case DOUBLE -> b.doubleVal(spec.name(), Double.parseDouble(raw.trim()));
            case BOOLEAN -> b.bool(spec.name(), parseBoolean(raw));
            case DATA_TYPE -> b.dataType(spec.name(), requireType(raw.trim()));
            case SUB_PIPELINE, SUB_PIPELINES_MAP, TYPED_SUB_PIPELINES_MAP -> { /* handled outside */ }
        }
    }

    private static boolean parseBoolean(@NotNull String raw) {
        String trimmed = raw.trim().toLowerCase();
        return switch (trimmed) {
            case "true", "yes", "y", "1", "on" -> true;
            case "false", "no", "n", "0", "off" -> false;
            default -> throw new IllegalArgumentException("expected true/false, got '" + raw + "'");
        };
    }

    private static @NotNull DataType<?> requireType(@NotNull String label) {
        DataType<?> t = DataTypes.byLabel(label);
        if (t == null) throw new IllegalArgumentException("unknown DataType label '" + label + "'");
        return t;
    }

    /**
     * Convenience: parse and invoke {@link StageReflection#of(Class)}'s {@code fromConfig} in
     * one call.
     *
     * @param stageClass the stage implementation class
     * @param values the submitted text values
     * @return success carrying the new stage, or failure with a banner-ready message
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static @NotNull StageResult parseAndBuild(@NotNull Class<? extends Stage> stageClass, @NotNull Map<String, String> values) {
        Class<? extends Stage<?, ?>> typed = (Class<? extends Stage<?, ?>>) stageClass;
        Result parsed = parseTyped(typed, values);
        if (!parsed.ok()) return new StageResult(null, parsed.error());
        try {
            Stage<?, ?> stage = StageReflection.of(typed).fromConfig(parsed.config());
            return new StageResult(stage, null);
        } catch (RuntimeException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return new StageResult(null, "Failed to build stage: " + cause.getMessage());
        }
    }

    /**
     * Outcome of a parse-and-build attempt.
     *
     * @param stage the built stage, {@code null} on error
     * @param error the error message, {@code null} on success
     */
    public record StageResult(@Nullable Stage<?, ?> stage, @Nullable String error) {

        /** {@code true} when the parse-and-build succeeded. */
        public boolean ok() {
            return this.error == null;
        }

    }

}
