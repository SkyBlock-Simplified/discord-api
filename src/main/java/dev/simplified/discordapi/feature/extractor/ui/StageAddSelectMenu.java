package dev.simplified.discordapi.feature.extractor.ui;

import dev.simplified.dataflow.stage.Stage;
import dev.simplified.dataflow.stage.StageRegistry;
import dev.simplified.dataflow.stage.meta.StageSpec;
import dev.simplified.discordapi.component.interaction.SelectMenu;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Two-stage palette for the "Add Stage" flow: a top-level {@link SelectMenu.StringMenu} listing
 * {@link StageSpec.Category categories}, and a per-category menu listing the stage classes
 * within that category.
 * <p>
 * Discord caps a {@link SelectMenu.StringMenu} at 25 options. Each individual category fits
 * comfortably under that limit, so the cascading picker keeps the UI usable without resorting
 * to followups. The categories menu has at most one option per {@link StageSpec.Category}
 * value that has at least one registered stage.
 * <p>
 * Both menus are returned without {@code onInteract} handlers - the caller wires those when
 * binding the menu inside a {@code Response}, closing over the live builder state.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StageAddSelectMenu {

    /** Component id of the category-picker menu. */
    public static final @NotNull String ID_CATEGORY = "extractor.builder.add.category";

    /** Identifier prefix of a per-category kind picker; suffix is the {@link StageSpec.Category} name. */
    public static final @NotNull String ID_KIND_PREFIX = "extractor.builder.add.kind.";

    /** Component id of the category picker scoped to a Branch sub-chain ({@code COLLECT_MAP} excluded). */
    public static final @NotNull String ID_CATEGORY_SUB_CHAIN = "extractor.builder.add.category.sub";

    /**
     * Builds the top-level category picker.
     *
     * @return the category select menu
     */
    public static @NotNull SelectMenu.StringMenu categories() {
        return categories(false);
    }

    /**
     * Builds the category picker scoped to a Branch sub-chain. The terminal-collect group's
     * {@code COLLECT_MAP} kind is hidden so the v1 depth-1 cap is enforced at the UI surface.
     * <p>
     * Because the underlying {@link StageSpec.Category} now lumps {@code COLLECT_MAP} into
     * {@link StageSpec.Category#TERMINAL_COLLECT} alongside {@code COLLECT_FIRST} etc., the
     * sub-chain palette drops the entire {@code TERMINAL_COLLECT} category to keep the
     * palette honest about what is actually pickable inside a sub-chain.
     *
     * @return the sub-chain category select menu
     */
    public static @NotNull SelectMenu.StringMenu categoriesForSubChain() {
        return categories(true);
    }

    private static @NotNull SelectMenu.StringMenu categories(boolean inSubChain) {
        SelectMenu.StringMenu.Builder menu = SelectMenu.builder()
            .withIdentifier(inSubChain ? ID_CATEGORY_SUB_CHAIN : ID_CATEGORY)
            .withPlaceholder("Add stage - choose category");

        for (StageSpec.Category category : StageSpec.Category.values()) {
            if (StageRegistry.ofCategory(category).isEmpty()) continue;
            if (inSubChain && category == StageSpec.Category.TERMINAL_COLLECT) continue;
            menu.withOptions(SelectMenu.Option.builder()
                .withLabel(displayName(category))
                .withValue(category.name())
                .build());
        }

        return menu.build();
    }

    /**
     * Builds a kind picker scoped to the given category.
     *
     * @param category the category whose stage classes should be listed
     * @return the kind select menu
     */
    public static @NotNull SelectMenu.StringMenu kindsIn(@NotNull StageSpec.Category category) {
        SelectMenu.StringMenu.Builder menu = SelectMenu.builder()
            .withIdentifier(ID_KIND_PREFIX + category.name())
            .withPlaceholder("Add stage - choose " + displayName(category) + " kind");

        for (Class<? extends Stage<?, ?>> stageClass : StageRegistry.ofCategory(category)) {
            StageSpec spec = stageClass.getAnnotation(StageSpec.class);
            menu.withOptions(SelectMenu.Option.builder()
                .withLabel(spec.displayName())
                .withDescription(spec.description())
                .withValue(spec.id())
                .build());
        }

        return menu.build();
    }

    private static @NotNull String displayName(@NotNull StageSpec.Category category) {
        String[] parts = category.name().toLowerCase().split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

}
