package dev.simplified.discordapi.integration.test;

import dev.simplified.discordapi.component.Component;
import dev.simplified.discordapi.handler.response.CachedResponse;
import dev.simplified.discordapi.harness.rest.RecordedRequest;
import dev.simplified.discordapi.harness.rest.RenderedMessage;
import dev.simplified.discordapi.integration.IntegrationHarness;
import dev.simplified.discordapi.integration.command.PaginatedCommand;
import dev.simplified.discordapi.response.handler.Filter;
import dev.simplified.discordapi.response.handler.ItemHandler;
import dev.simplified.discordapi.response.page.Page;
import discord4j.common.util.Snowflake;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the {@link PaginatedCommand} broad-Response fixture: page-selector navigation, subpage nav + BACK,
 * item pagination, the sort/filter modals end-to-end (verifying the unified modal-submit dispatch wires the
 * folded selection into the sort/filter handlers), the search modal, and the button/menu/option contexts.
 *
 * <p>
 * The pagination/nav components carry random-UUID ids, so each step discovers the id it needs from a
 * {@link RenderedMessage} of the current render (by label or placeholder) before clicking it. The current item
 * slice renders as a {@code Container} of {@code Section}s, so item outcomes are asserted both against the
 * rendered body ({@code |row-N|} markers) and against the cached response's live handler state
 * (sort/filter/index), which is exactly what the interactions mutate.
 */
class PaginatedResponseInteractionTest {

    private static final String PAGE_SELECTOR_PLACEHOLDER = "Select a page.";
    private static final String SUBPAGE_SELECTOR_PLACEHOLDER = "Select a subpage.";

    @Test
    void renders_page_selector_and_pagination_controls() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            harness.sendSlashCommand("paginated");
            RenderedMessage rendered = RenderedMessage.of(harness.awaitInteractionReply());

            assertTrue(rendered.customIdByPlaceholder(PAGE_SELECTOR_PLACEHOLDER).isPresent(), "page selector present");
            assertFalse(rendered.isDisabled(rendered.customIdByPlaceholder(PAGE_SELECTOR_PLACEHOLDER).orElseThrow()), "the page selector renders enabled (Toggleable enabled-state fix)");
            assertTrue(rendered.customIdByLabel("Previous").isPresent(), "previous button present");
            assertTrue(rendered.customIdByLabel("Sort").isPresent(), "sort button present");
            assertTrue(rendered.customIdByLabel("Filter").isPresent(), "filter button present");
            assertTrue(rendered.customIdByLabel("Next").isPresent(), "next button present");
            assertTrue(rendered.customIdByLabel("1 / 2").isPresent(), "index button shows the page count");
            assertFalse(rendered.isDisabled(rendered.customIdByLabel("1 / 2").orElseThrow()), "the search (index) button renders enabled");

            // the current item slice renders as sections (ascending sort, 5 per page -> rows 0..4)
            assertTrue(rendered.textContains("|row-0|"), "the first item renders");
            assertTrue(rendered.textContains("|row-4|"), "the last item of page 1 renders");
            assertFalse(rendered.textContains("|row-5|"), "page 2 items do not render on page 1");
        }
    }

    @Test
    void page_selector_navigates_between_pages() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            long message = harness.config().getReplyMessageId();
            harness.sendSlashCommand("paginated");
            String selector = RenderedMessage.of(harness.awaitInteractionReply()).customIdByPlaceholder(PAGE_SELECTOR_PLACEHOLDER).orElseThrow();

            harness.clickSelectMenu(message, selector, PaginatedCommand.PAGE_CONTROLS);
            RenderedMessage controls = RenderedMessage.of(reRender(harness, "select", selector));

            assertEquals(PaginatedCommand.PAGE_CONTROLS, currentPage(harness, message).getOption().getValue(), "the controls page is now current");
            assertTrue(controls.hasComponent(PaginatedCommand.BUTTON_ID), "the controls page's button rendered");
            assertTrue(controls.textContains("Controls"), "the controls page content rendered");
        }
    }

    @Test
    void button_menu_and_option_contexts_all_fire() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            long message = harness.config().getReplyMessageId();
            harness.sendSlashCommand("paginated");
            String selector = RenderedMessage.of(harness.awaitInteractionReply()).customIdByPlaceholder(PAGE_SELECTOR_PLACEHOLDER).orElseThrow();

            // navigate to the controls page, then exercise the button and the menu/option handlers
            harness.clickSelectMenu(message, selector, PaginatedCommand.PAGE_CONTROLS);
            reRender(harness, "select", selector);

            harness.clickButton(message, PaginatedCommand.BUTTON_ID);
            harness.awaitRequest(request -> request.path().contains("button-token-" + PaginatedCommand.BUTTON_ID));
            assertTrue(loaded(harness).isButtonRan(), "the button context handler ran");

            harness.clickSelectMenu(message, PaginatedCommand.MENU_ID, "first");
            harness.awaitRequest(request -> request.path().contains("select-token-" + PaginatedCommand.MENU_ID));
            assertTrue(loaded(harness).isMenuRan(), "the menu-level context handler ran");
            assertEquals("first", loaded(harness).getOptionPicked(), "the per-option context handler ran");

            assertEquals(1, harness.callbackCount("button-token-" + PaginatedCommand.BUTTON_ID), "the button acknowledges exactly once");
        }
    }

    @Test
    void item_next_advances_the_current_index() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            long message = harness.config().getReplyMessageId();
            harness.sendSlashCommand("paginated");
            String next = RenderedMessage.of(harness.awaitInteractionReply()).customIdByLabel("Next").orElseThrow();

            assertEquals(1, itemHandler(harness, message).getCurrentIndex(), "starts on item page 1");

            harness.clickButton(message, next);
            // an inline pagination button edits via a type-7 update callback (no deferEdit), so the re-render
            // body lands on the .../callback POST rather than a webhook PATCH
            RenderedMessage advanced = RenderedMessage.of(harness.awaitRequest(
                request -> request.path().contains("button-token-" + next) && request.path().endsWith("/callback")
            ));

            assertEquals(2, itemHandler(harness, message).getCurrentIndex(), "next advances to item page 2");
            assertTrue(advanced.textContains("|row-5|"), "page 2 items now render");
            assertFalse(advanced.textContains("|row-0|"), "page 1 items no longer render");
        }
    }

    @Test
    void sort_modal_submit_changes_the_current_sorter() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            long message = harness.config().getReplyMessageId();
            harness.sendSlashCommand("paginated");
            String sort = RenderedMessage.of(harness.awaitInteractionReply()).customIdByLabel("Sort").orElseThrow();

            assertEquals(PaginatedCommand.SORTER_UP, currentSorter(harness, message), "starts on the first sorter");

            harness.clickButton(message, sort);
            RenderedMessage modal = RenderedMessage.of(harness.awaitRequest(
                request -> request.path().contains("button-token-" + sort) && request.path().endsWith("/callback")
            ));
            String modalId = modal.rootCustomId().orElseThrow();
            String radioId = modal.customIdsByType(Component.Type.RADIO_GROUP.getValue()).getFirst();

            harness.submitModal(message, modalId).withRadio(radioId, PaginatedCommand.SORTER_DOWN).submit();
            RenderedMessage sorted = RenderedMessage.of(harness.awaitRequest(
                request -> request.path().contains("modal-token-" + modalId) && request.path().endsWith("/callback")
            ));

            assertEquals(PaginatedCommand.SORTER_DOWN, currentSorter(harness, message), "the sort submit selects the chosen sorter");
            assertTrue(sorted.textContains("|row-9|"), "descending sort renders the highest row on page 1");
            assertFalse(sorted.textContains("|row-0|"), "the ascending-first rows move off page 1 under descending sort");
        }
    }

    @Test
    void filter_modal_submit_enables_the_chosen_filter() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            long message = harness.config().getReplyMessageId();
            harness.sendSlashCommand("paginated");
            String filter = RenderedMessage.of(harness.awaitInteractionReply()).customIdByLabel("Filter").orElseThrow();

            assertFalse(filterEnabled(harness, message, PaginatedCommand.FILTER_HIGH), "the high filter starts disabled");

            harness.clickButton(message, filter);
            RenderedMessage modal = RenderedMessage.of(harness.awaitRequest(
                request -> request.path().contains("button-token-" + filter) && request.path().endsWith("/callback")
            ));
            String modalId = modal.rootCustomId().orElseThrow();
            String checkboxGroupId = modal.customIdsByType(Component.Type.CHECKBOX_GROUP.getValue()).getFirst();

            harness.submitModal(message, modalId).withCheckboxGroup(checkboxGroupId, PaginatedCommand.FILTER_HIGH).submit();
            RenderedMessage filtered = RenderedMessage.of(harness.awaitRequest(
                request -> request.path().contains("modal-token-" + modalId) && request.path().endsWith("/callback")
            ));

            assertTrue(filterEnabled(harness, message, PaginatedCommand.FILTER_HIGH), "the filter submit enables the chosen filter");
            assertFalse(filterEnabled(harness, message, PaginatedCommand.FILTER_LOW), "the unchosen filter is disabled");
            assertTrue(filtered.textContains("|row-5|"), "the high-half filter renders row 5");
            assertFalse(filtered.textContains("|row-0|"), "the low-half rows are filtered out");
        }
    }

    @Test
    void search_modal_page_jump_changes_the_index() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            long message = harness.config().getReplyMessageId();
            harness.sendSlashCommand("paginated");
            String index = RenderedMessage.of(harness.awaitInteractionReply()).customIdByLabel("1 / 2").orElseThrow();

            harness.clickButton(message, index);
            RenderedMessage modal = RenderedMessage.of(harness.awaitRequest(
                request -> request.path().contains("button-token-" + index) && request.path().endsWith("/callback")
            ));
            String modalId = modal.rootCustomId().orElseThrow();
            // the page search input is the first text input in the search modal
            String pageInput = modal.customIdsByType(Component.Type.TEXT_INPUT.getValue()).getFirst();

            harness.submitModal(message, modalId).withText(pageInput, "2").submit();
            harness.awaitRequest(request -> request.path().contains("modal-token-" + modalId));

            assertEquals(2, itemHandler(harness, message).getCurrentIndex(), "the page search jumps to page 2");
        }
    }

    @Test
    void subpage_navigation_and_back() {
        try (IntegrationHarness harness = new IntegrationHarness().boot(Duration.ofSeconds(30))) {
            long message = harness.config().getReplyMessageId();
            harness.sendSlashCommand("paginated");
            String selector = RenderedMessage.of(harness.awaitInteractionReply()).customIdByPlaceholder(PAGE_SELECTOR_PLACEHOLDER).orElseThrow();

            // navigate to the page that carries a subpage
            harness.clickSelectMenu(message, selector, PaginatedCommand.PAGE_TREE);
            RenderedMessage tree = RenderedMessage.of(reRender(harness, "select", selector));
            String subSelector = tree.customIdByPlaceholder(SUBPAGE_SELECTOR_PLACEHOLDER).orElseThrow();

            // into the subpage
            harness.clickSelectMenu(message, subSelector, PaginatedCommand.SUBPAGE_CHILD);
            reRender(harness, "select", subSelector);
            assertEquals(PaginatedCommand.SUBPAGE_CHILD, currentPage(harness, message).getOption().getValue(), "navigated into the subpage");

            // BACK to the parent - discover the (rebuilt) subpage selector again
            String subSelectorAgain = RenderedMessage.of(latestEdit(harness)).customIdByPlaceholder(SUBPAGE_SELECTOR_PLACEHOLDER).orElseThrow();
            harness.clickSelectMenu(message, subSelectorAgain, "BACK");
            reRender(harness, "select", subSelectorAgain);
            assertEquals(PaginatedCommand.PAGE_TREE, currentPage(harness, message).getOption().getValue(), "BACK returns to the parent page");
        }
    }

    // --- helpers ---

    private static RecordedRequest reRender(IntegrationHarness harness, String tokenPrefix, String customId) {
        // a component nav deferEdits (type 6) then webhook-edits the message, so the render body lands on the
        // PATCH .../<token>/messages/@original, not the deferred callback
        return harness.awaitRequest(request -> request.path().contains(tokenPrefix + "-token-" + customId)
            && request.path().endsWith("/messages/@original"));
    }

    private static RecordedRequest latestEdit(IntegrationHarness harness) {
        return harness.awaitInteractionReply();
    }

    private static Page currentPage(IntegrationHarness harness, long messageId) {
        harness.awaitResponseCached(messageId, Duration.ofSeconds(5));
        CachedResponse cached = harness.bot().getResponseLocator().findByMessage(Snowflake.of(messageId)).block(Duration.ofSeconds(5));
        return cached.getResponse().getHistoryHandler().getCurrentPage();
    }

    private static ItemHandler<?> itemHandler(IntegrationHarness harness, long messageId) {
        return currentPage(harness, messageId).getItemHandler();
    }

    private static String currentSorter(IntegrationHarness harness, long messageId) {
        return itemHandler(harness, messageId).getSortHandler().getCurrent().orElseThrow().getIdentifier();
    }

    private static boolean filterEnabled(IntegrationHarness harness, long messageId, String identifier) {
        return itemHandler(harness, messageId).getFilterHandler().getItems().stream()
            .filter(item -> item.getIdentifier().equals(identifier))
            .findFirst()
            .map(Filter::isEnabled)
            .orElseThrow();
    }

    private static PaginatedCommand loaded(IntegrationHarness harness) {
        return harness.bot().getCommandHandler().getLoadedCommands().stream()
            .filter(PaginatedCommand.class::isInstance)
            .map(PaginatedCommand.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError("PaginatedCommand not loaded"));
    }

}
