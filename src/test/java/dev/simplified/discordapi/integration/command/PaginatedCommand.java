package dev.simplified.discordapi.integration.command;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.query.SortOrder;
import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.Structure;
import dev.simplified.discordapi.component.TextDisplay;
import dev.simplified.discordapi.component.interaction.Button;
import dev.simplified.discordapi.component.interaction.SelectMenu;
import dev.simplified.discordapi.component.layout.ActionRow;
import dev.simplified.discordapi.component.layout.Section;
import dev.simplified.discordapi.context.command.SlashCommandContext;
import dev.simplified.discordapi.exception.DiscordException;
import dev.simplified.discordapi.response.handler.Filter;
import dev.simplified.discordapi.response.handler.ItemHandler;
import dev.simplified.discordapi.response.handler.Sorter;
import dev.simplified.discordapi.response.page.Page;
import dev.simplified.discordapi.response.page.TreePage;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * A global {@code /paginated} slash command that replies with a multi-page {@link TreePage} response covering
 * the broad Response feature surface: a page selector over three top-level pages, an item-paginated page with
 * two sorters and two filters (driving prev/next/sort/filter/search), a controls page carrying a button and a
 * select menu (component contexts), and a page with a subpage (subpage nav + BACK).
 */
@Structure(
    name = "paginated",
    description = "A multi-page paginated response"
)
public class PaginatedCommand extends DiscordCommand<SlashCommandContext> {

    /** Nav value of the item-paginated page (the default landing page). */
    public static final @NotNull String PAGE_ITEMS = "items";
    /** Nav value of the controls page. */
    public static final @NotNull String PAGE_CONTROLS = "controls";
    /** Nav value of the page carrying a subpage. */
    public static final @NotNull String PAGE_TREE = "tree";
    /** Nav value of the subpage under {@link #PAGE_TREE}. */
    public static final @NotNull String SUBPAGE_CHILD = "child";

    /** The button on the controls page. */
    public static final @NotNull String BUTTON_ID = "paginated:button";
    /** The select menu on the controls page. */
    public static final @NotNull String MENU_ID = "paginated:menu";

    /** Identifier of the ascending sorter (the initial sort). */
    public static final @NotNull String SORTER_UP = "up";
    /** Identifier of the descending sorter. */
    public static final @NotNull String SORTER_DOWN = "down";
    /** Identifier of the low-half filter (rows &lt; 5). */
    public static final @NotNull String FILTER_LOW = "low";
    /** Identifier of the high-half filter (rows &gt;= 5). */
    public static final @NotNull String FILTER_HIGH = "high";

    /** Whether the controls button handler ran. */
    @Getter private volatile boolean buttonRan = false;
    /** Whether the controls menu-level handler ran. */
    @Getter private volatile boolean menuRan = false;
    /** The option value the controls per-option handler captured, or {@code null}. */
    @Getter private volatile String optionPicked = null;

    public PaginatedCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** {@inheritDoc} */
    @Override
    protected @NotNull Mono<Void> process(@NotNull SlashCommandContext commandContext) throws DiscordException {
        return commandContext.reply(
            commandContext.buildResponse()
                .withTimeToLive(120)
                .withPages(this.itemsPage(), this.controlsPage(), this.treePage())
                .build()
        );
    }

    private TreePage itemsPage() {
        ConcurrentList<Integer> rows = Concurrent.newList();
        for (int index = 0; index < 10; index++)
            rows.add(index);

        ItemHandler<Integer> items = ItemHandler.<Integer>builder()
            .withItems(rows)
            .withAmountPerPage(5)
            // A Section requires an accessory (Discord Components V2), so each item row carries an inert
            // secondary button alongside its "|row-N|" text display.
            .withTransformer((row, index, size) -> Section.builder()
                .withAccessory(
                    Button.builder()
                        .withStyle(Button.Style.SECONDARY)
                        .withLabel("Open")
                        .withIdentifier("paginated:row:" + row)
                        .build()
                )
                .withComponents(TextDisplay.of("|row-" + row + "|"))
                .build())
            // The net sort direction is the Sorter's top-level order (withOrder); the per-comparator order is
            // kept neutral (ASCENDING = natural) so the label matches behavior: ASCENDING -> 0..9, DESCENDING -> 9..0.
            .withSorters(
                Sorter.<Integer>builder().withIdentifier(SORTER_UP).withLabel("Ascending").isEnabled()
                    .withOrder(SortOrder.ASCENDING).withFunctions(SortOrder.ASCENDING, row -> row).build(),
                Sorter.<Integer>builder().withIdentifier(SORTER_DOWN).withLabel("Descending").isEnabled()
                    .withOrder(SortOrder.DESCENDING).withFunctions(SortOrder.ASCENDING, row -> row).build()
            )
            .withFilters(
                Filter.<Integer>builder().withIdentifier(FILTER_LOW).withLabel("Low half")
                    .withPredicates(row -> row < 5).build(),
                Filter.<Integer>builder().withIdentifier(FILTER_HIGH).withLabel("High half")
                    .withPredicates(row -> row >= 5).build()
            )
            .build();

        return TreePage.builder()
            .withLabel("Items")
            .withValue(PAGE_ITEMS)
            .withContent("Rows")
            .withItemHandler(items)
            .build();
    }

    private TreePage controlsPage() {
        Button button = Button.builder()
            .withStyle(Button.Style.PRIMARY)
            .withLabel("Press")
            .withIdentifier(BUTTON_ID)
            .onInteract(context -> {
                this.buttonRan = true;
                return context.edit(response -> response.editCurrentPage(page -> page.withContent("pressed")));
            })
            .build();

        SelectMenu menu = SelectMenu.builder()
            .withIdentifier(MENU_ID)
            .withPlaceholder("Pick one")
            .withOptions(
                SelectMenu.Option.builder().withLabel("First").withValue("first")
                    .onInteract(context -> {
                        this.optionPicked = context.getOption().getValue();
                        return context.deferEdit();
                    }).build(),
                SelectMenu.Option.builder().withLabel("Second").withValue("second")
                    .onInteract(context -> {
                        this.optionPicked = context.getOption().getValue();
                        return context.deferEdit();
                    }).build()
            )
            .onInteract(context -> {
                this.menuRan = true;
                return Mono.empty();
            })
            .build();

        return TreePage.builder()
            .withLabel("Controls")
            .withValue(PAGE_CONTROLS)
            .withContent("Controls")
            .withComponents(ActionRow.of(button), ActionRow.of(menu))
            .build();
    }

    private TreePage treePage() {
        // The child is a leaf subpage (no subpages of its own): the framework renders the subpage selector -
        // and its BACK option - for any subpage that has page history, so a leaf is no longer a dead-end.
        TreePage child = TreePage.builder()
            .withLabel("Child")
            .withValue(SUBPAGE_CHILD)
            .withContent("Child page")
            .build();

        return TreePage.builder()
            .withLabel("Tree")
            .withValue(PAGE_TREE)
            .withContent("Parent page")
            .withPages(child)
            .build();
    }

}
