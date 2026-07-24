package dev.simplified.discordapi.feature.extractor.ui;

import dev.simplified.discordapi.component.interaction.Modal;
import dev.simplified.discordapi.component.interaction.TextInput;
import dev.simplified.discordapi.component.layout.Label;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * "Save as..." modal for the pipeline builder. Collects the human-readable label, the
 * short id used by {@code /extract <shortId>}, and the visibility scope.
 * <p>
 * Visibility is collected as a free-form text input (one of {@code PRIVATE}, {@code GUILD},
 * {@code PUBLIC}) since Discord modals do not host a select menu. A future enhancement could
 * present a separate select-page for the visibility step; for v1 the typed input keeps the
 * save flow on a single confirmation surface.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SaveExtractorModal {

    /** Component identifier for the save modal. */
    public static final @NotNull String ID = "extractor.builder.save.modal";

    /** Identifier for the label input slot. */
    public static final @NotNull String FIELD_LABEL = "label";

    /** Identifier for the short id input slot. */
    public static final @NotNull String FIELD_SHORT_ID = "shortId";

    /** Identifier for the visibility input slot. */
    public static final @NotNull String FIELD_VISIBILITY = "visibility";

    /**
     * Builds the save modal pre-filled from the current builder state.
     *
     * @param state the current builder state
     * @return the save modal
     */
    public static @NotNull Modal of(@NotNull PipelineBuilderState state) {
        return Modal.builder()
            .withIdentifier(ID)
            .withTitle("Save extractor")
            .withComponents(
                Label.builder()
                    .withTitle("Label")
                    .withDescription("Display name shown in the builder UI")
                    .withComponent(TextInput.builder()
                        .withIdentifier(FIELD_LABEL)
                        .withPlaceholder("Wiki Damage")
                        .withValue(state.label().isEmpty() ? null : state.label())
                        .withMaxLength(64)
                        .isRequired()
                        .build())
                    .build(),
                Label.builder()
                    .withTitle("Short id")
                    .withDescription("Used as /extract <shortId>; lowercase, digits, underscores")
                    .withComponent(TextInput.builder()
                        .withIdentifier(FIELD_SHORT_ID)
                        .withPlaceholder("wiki_dmg")
                        .withValue(state.shortId().isEmpty() ? null : state.shortId())
                        .withMaxLength(32)
                        .isRequired()
                        .build())
                    .build(),
                Label.builder()
                    .withTitle("Visibility")
                    .withDescription("PRIVATE / GUILD / PUBLIC")
                    .withComponent(TextInput.builder()
                        .withIdentifier(FIELD_VISIBILITY)
                        .withPlaceholder("PRIVATE")
                        .withValue("PRIVATE")
                        .withMaxLength(8)
                        .isRequired()
                        .build())
                    .build()
            )
            .build();
    }

}
