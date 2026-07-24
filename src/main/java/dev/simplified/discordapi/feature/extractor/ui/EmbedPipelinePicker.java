package dev.simplified.discordapi.feature.extractor.ui;

import dev.simplified.discordapi.component.interaction.SelectMenu;
import dev.simplified.discordapi.feature.extractor.Extractor;
import dev.simplified.discordapi.feature.extractor.ExtractorStore;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

/**
 * Builds a {@link SelectMenu.StringMenu} listing every saved {@link Extractor} the caller may
 * embed as a stage. Each option's value is the extractor's UUID, label is the extractor's
 * display label plus its output type for at-a-glance compatibility.
 * <p>
 * Discord caps {@link SelectMenu.StringMenu} at 25 options. The picker takes the first 25
 * accessible extractors sorted by label - usually plenty for a single guild's catalogue.
 * Bots that outgrow that should split into category subgroups via followups (a v2 concern).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EmbedPipelinePicker {

    /** Component id of the embed picker. */
    public static final @NotNull String ID = "extractor.builder.add.embed";

    /** Discord cap on string-select options. */
    public static final int OPTION_CAP = 25;

    /**
     * Builds the picker against {@code store}, scoped to {@code callerUserId} +
     * {@code callerGuildId}.
     *
     * @param store the extractor store
     * @param callerUserId the calling user's snowflake
     * @param callerGuildId the calling user's guild snowflake, or {@code null} in DM
     * @return a Mono emitting the picker, or empty when no embeddable extractors are visible
     */
    public static @NotNull Mono<SelectMenu.StringMenu> of(
        @NotNull ExtractorStore store,
        long callerUserId,
        @Nullable Long callerGuildId
    ) {
        return store.findVisible(callerUserId, callerGuildId)
            .take(OPTION_CAP)
            .collectList()
            .flatMap(rows -> {
                if (rows.isEmpty()) return Mono.empty();
                SelectMenu.StringMenu.Builder menu = SelectMenu.builder()
                    .withIdentifier(ID)
                    .withPlaceholder("Embed pipeline - choose a saved extractor");
                for (Extractor row : rows)
                    menu.withOptions(SelectMenu.Option.builder()
                        .withLabel(row.getLabel().isEmpty() ? row.getShortId() : row.getLabel())
                        .withDescription(describe(row))
                        .withValue(row.getId().toString())
                        .build());
                return Mono.just(menu.build());
            });
    }

    private static @NotNull String describe(@NotNull Extractor row) {
        return "/extract " + row.getShortId();
    }

}
