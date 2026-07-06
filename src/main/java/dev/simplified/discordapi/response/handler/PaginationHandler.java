package dev.simplified.discordapi.response.handler;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.discordapi.component.Component;
import dev.simplified.discordapi.component.interaction.Button;
import dev.simplified.discordapi.component.interaction.CheckboxGroup;
import dev.simplified.discordapi.component.interaction.Modal;
import dev.simplified.discordapi.component.interaction.RadioGroup;
import dev.simplified.discordapi.component.interaction.SelectMenu;
import dev.simplified.discordapi.component.interaction.TextInput;
import dev.simplified.discordapi.component.layout.ActionRow;
import dev.simplified.discordapi.component.layout.Label;
import dev.simplified.discordapi.component.scope.LayoutComponent;
import dev.simplified.discordapi.component.scope.TopLevelMessageComponent;
import dev.simplified.discordapi.context.component.ButtonContext;
import dev.simplified.discordapi.context.component.ModalContext;
import dev.simplified.discordapi.context.component.SelectMenuContext;
import dev.simplified.discordapi.response.Emoji;
import dev.simplified.discordapi.response.EmojiResolver;
import dev.simplified.discordapi.response.page.Page;
import dev.simplified.util.NumberUtil;
import dev.simplified.util.Range;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds pagination components with emoji access and provides interaction handlers
 * for paginated responses.
 *
 * <p>
 * Centralizes the creation of navigation buttons, page/subpage select menus, and
 * sort/filter/search modals that were previously scattered across
 * {@link Button.PageType}, {@link SelectMenu.PageType}, and
 * {@link TextInput.SearchType} enums.
 *
 * @see Button.PageType
 * @see SelectMenu.PageType
 * @see TextInput.SearchType
 */
public class PaginationHandler {

    // --- Button Interactions ---

    /**
     * Returns the interaction handler for the {@link Button.PageType#PREVIOUS} button.
     *
     * @return the previous-page interaction
     */
    public static @NotNull Function<ButtonContext, Mono<Void>> previousPageInteraction() {
        return context -> context.consumeResponse(response -> response.getHistoryHandler()
            .getCurrentPage()
            .getItemHandler()
            .gotoPreviousPage()
        );
    }

    /**
     * Returns the interaction handler for the {@link Button.PageType#SORT} button.
     * Presents a modal with a {@link RadioGroup} showing available sorters.
     *
     * @return the sort modal interaction
     */
    public static @NotNull Function<ButtonContext, Mono<Void>> sortInteraction() {
        return context -> context.withResponse(response -> {
            ItemHandler<?> itemHandler = response.getHistoryHandler().getCurrentPage().getItemHandler();
            SortHandler<?> sortHandler = itemHandler.getSortHandler();

            RadioGroup.Builder radioBuilder = RadioGroup.builder().onSubmit(PaginationHandler::applySortSelection);
            sortHandler.getItems().forEach(sorter -> radioBuilder.withOptions(sorter.buildOption()));

            RadioGroup radioGroup = radioBuilder.build();
            sortHandler.getCurrent().ifPresent(current -> radioGroup.updateSelected(current.getIdentifier()));

            return context.presentModal(
                Modal.builder()
                    .withTitle("Sort")
                    .withComponents(Label.builder().withTitle("Sort By").withComponent(radioGroup).build())
                    .build()
            );
        });
    }

    /**
     * Reads the sorter chosen in the submitted Sort modal and makes it the current sorter,
     * re-triggering the item pipeline on the next render.
     *
     * @param context the modal submit context
     * @param radioGroup the folded sort radio group carrying the user's pick
     * @return the reactive completion
     */
    private static @NotNull Mono<Void> applySortSelection(@NotNull ModalContext context, @NotNull RadioGroup radioGroup) {
        return context.consumeResponse(response -> radioGroup.getSelected()
            .map(RadioGroup.Option::getValue)
            .ifPresent(sorterId -> response.getHistoryHandler()
                .getCurrentPage()
                .getItemHandler()
                .getSortHandler()
                .setCurrent(sorterId)
            )
        );
    }

    /**
     * Presents a modal with page/index text inputs and custom search fields.
     *
     * @return the search modal interaction
     * @see Button.PageType#INDEX
     */
    public static @NotNull Function<ButtonContext, Mono<Void>> searchInteraction() {
        return context -> context.withResponse(response -> {
            ItemHandler<?> itemHandler = response.getHistoryHandler().getCurrentPage().getItemHandler();

            Modal.Builder modalBuilder = Modal.builder()
                .withComponents(
                    buildPageSearchLabel(itemHandler),
                    buildIndexSearchLabel(itemHandler)
                )
                .withTitle("Search");

            itemHandler.getSearchHandler()
                .getItems()
                .stream()
                .map(search -> Label.builder()
                    .withTitle("Search")
                    .withComponent(search.getTextInput())
                    .build()
                )
                .forEach(modalBuilder::withComponents);

            return context.presentModal(modalBuilder.build());
        });
    }

    /**
     * Returns the interaction handler for the {@link Button.PageType#FILTER} button.
     * Presents a modal with a {@link CheckboxGroup} showing available filters.
     *
     * @return the filter modal interaction
     */
    public static @NotNull Function<ButtonContext, Mono<Void>> filterInteraction() {
        return context -> context.withResponse(response -> {
            ItemHandler<?> itemHandler = response.getHistoryHandler().getCurrentPage().getItemHandler();
            FilterHandler<?> filterHandler = itemHandler.getFilterHandler();

            CheckboxGroup.Builder checkboxBuilder = CheckboxGroup.builder()
                .withMinValues(0)
                .withMaxValues(filterHandler.getItems().size())
                .onSubmit(PaginationHandler::applyFilterSelection);

            filterHandler.getItems().forEach(filter -> checkboxBuilder.withOptions(filter.buildOption()));

            CheckboxGroup checkboxGroup = checkboxBuilder.build();
            String[] enabledIds = filterHandler.getItems()
                .stream()
                .filter(Filter::isEnabled)
                .map(Filter::getIdentifier)
                .toArray(String[]::new);
            checkboxGroup.updateSelected(enabledIds);

            return context.presentModal(
                Modal.builder()
                    .withTitle("Filters")
                    .withComponents(Label.builder().withTitle("Active Filters").withComponent(checkboxGroup).build())
                    .build()
            );
        });
    }

    /**
     * Reads the filters checked in the submitted Filters modal and enables exactly those,
     * disabling the rest, re-triggering the item pipeline on the next render.
     *
     * @param context the modal submit context
     * @param checkboxGroup the folded filter checkbox group carrying the user's selection
     * @return the reactive completion
     */
    private static @NotNull Mono<Void> applyFilterSelection(@NotNull ModalContext context, @NotNull CheckboxGroup checkboxGroup) {
        return context.consumeResponse(response -> {
            Set<String> enabled = checkboxGroup.getSelected()
                .stream()
                .map(CheckboxGroup.Option::getValue)
                .collect(Collectors.toSet());

            response.getHistoryHandler()
                .getCurrentPage()
                .getItemHandler()
                .getFilterHandler()
                .applyEnabled(enabled);
        });
    }

    /**
     * Returns the interaction handler for the {@link Button.PageType#NEXT} button.
     *
     * @return the next-page interaction
     */
    public static @NotNull Function<ButtonContext, Mono<Void>> nextPageInteraction() {
        return context -> context.consumeResponse(response -> response.getHistoryHandler()
            .getCurrentPage()
            .getItemHandler()
            .gotoNextPage()
        );
    }

    // --- SelectMenu Interactions ---

    /**
     * Returns the interaction handler for the {@link SelectMenu.PageType#PAGE_SELECTOR} menu.
     *
     * @return the page selector interaction
     */
    public static @NotNull Function<SelectMenuContext, Mono<Void>> pageSelectionInteraction() {
        return context -> context.consumeResponse(response -> {
            String selectedValue = context.getSelectedValues().getFirst();
            response.getHistoryHandler().gotoTopLevelPage(selectedValue);
        });
    }

    /**
     * Returns the interaction handler for the {@link SelectMenu.PageType#SUBPAGE_SELECTOR} menu.
     *
     * @return the subpage selector interaction
     */
    public static @NotNull Function<SelectMenuContext, Mono<Void>> subpageSelectionInteraction() {
        return context -> context.consumeResponse(response -> {
            String selectedValue = context.getSelectedValues().getFirst();

            if (selectedValue.equals("BACK"))
                response.getHistoryHandler().gotoParentPage();
            else
                response.getHistoryHandler().gotoSubPage(selectedValue);
        });
    }

    // --- SearchType Interactions ---

    /**
     * Returns the interaction handler for page navigation via modal text input.
     *
     * @return the page navigation interaction
     */
    public static @NotNull Mono<Void> handlePageSearch(@NotNull ModalContext context, @NotNull TextInput textInput) {
        return context.consumeResponse(response -> {
            ItemHandler<?> itemHandler = context.getResponse().getHistoryHandler().getCurrentPage().getItemHandler();
            Range<Integer> pageRange = Range.between(1, itemHandler.getTotalPages());
            itemHandler.gotoPage(pageRange.fit(Integer.parseInt(textInput.getValue().orElseThrow())));
        });
    }

    /**
     * Returns the interaction handler for index navigation via modal text input.
     *
     * @return the index navigation interaction
     */
    public static @NotNull Mono<Void> handleIndexSearch(@NotNull ModalContext context, @NotNull TextInput textInput) {
        return context.consumeResponse(response -> {
            ItemHandler<?> itemHandler = context.getResponse().getHistoryHandler().getCurrentPage().getItemHandler();
            Range<Integer> indexRange = Range.between(0, itemHandler.getCachedFilteredItems().size());
            int index = indexRange.fit(Integer.parseInt(textInput.getValue().orElseThrow()));
            itemHandler.gotoPage((int) Math.ceil((double) index / itemHandler.getAmountPerPage()));
        });
    }

    /**
     * Returns the interaction handler for custom search via modal text input.
     *
     * @return the custom search interaction
     */
    public static @NotNull Mono<Void> handleCustomSearch(@NotNull ModalContext context, @NotNull TextInput textInput) {
        return context.consumeResponse(response -> context.getResponse()
            .getHistoryHandler()
            .getCurrentPage()
            .getItemHandler()
            .getSearchHandler()
            .search(textInput)
        );
    }

    // --- Component Builders ---

    /**
     * Builds a pagination button for the given page type without an emoji.
     *
     * @param pageType the page type identifier
     * @return the built button
     */
    public static @NotNull Button buildButton(@NotNull Button.PageType pageType) {
        return buildButton(pageType, Optional.empty());
    }

    /**
     * Builds a pagination button for the given page type with an optional emoji.
     *
     * @param pageType the page type identifier
     * @param emoji the optional emoji
     * @return the built button
     */
    public static @NotNull Button buildButton(@NotNull Button.PageType pageType, @NotNull Optional<Emoji> emoji) {
        return Button.builder()
            .withStyle(Button.Style.SECONDARY)
            .withEmoji(emoji)
            .withLabel(pageType.getLabel())
            .withPageType(pageType)
            .setDisabled(true)
            .onInteract(getButtonInteraction(pageType))
            .build();
    }

    /**
     * Builds all pagination buttons without emojis.
     *
     * @return the list of pagination buttons
     */
    public static @NotNull ConcurrentList<Button> buildPaginationButtons() {
        return Concurrent.newList(
            buildButton(Button.PageType.PREVIOUS),
            buildButton(Button.PageType.SORT),
            buildButton(Button.PageType.INDEX),
            buildButton(Button.PageType.FILTER),
            buildButton(Button.PageType.NEXT)
        );
    }

    /**
     * Builds all pagination buttons, resolving each button's emoji through the given resolver. The INDEX
     * button opens the search modal, so it carries the {@code SEARCH} emoji; the SORT button carries
     * {@code SORT}.
     *
     * @param emojis the emoji resolver
     * @return the list of pagination buttons
     */
    public @NotNull ConcurrentList<Button> buildPaginationButtons(@NotNull EmojiResolver emojis) {
        return Concurrent.newList(
            buildButton(Button.PageType.PREVIOUS, emojis.getEmoji("ARROW_LEFT")),
            buildButton(Button.PageType.SORT, emojis.getEmoji("SORT")),
            buildButton(Button.PageType.INDEX, emojis.getEmoji("SEARCH")),
            buildButton(Button.PageType.FILTER, emojis.getEmoji("FILTER")),
            buildButton(Button.PageType.NEXT, emojis.getEmoji("ARROW_RIGHT"))
        );
    }

    /**
     * Builds a Label wrapping a TextInput for page navigation search.
     *
     * @param itemHandler the item handler
     * @return the label containing the page search text input
     */
    public static @NotNull Label buildPageSearchLabel(@NotNull ItemHandler<?> itemHandler) {
        return Label.builder()
            .withTitle("Go to Page")
            .withComponent(
                TextInput.builder()
                    .withStyle(TextInput.Style.SHORT)
                    .withSearchType(TextInput.SearchType.PAGE)
                    .withPlaceholder("Enter a number between 1 and %d.", itemHandler.getTotalPages())
                    .withValidator(value -> {
                        if (!NumberUtil.isCreatable(value))
                            return false;

                        Range<Integer> pageRange = Range.between(1, itemHandler.getTotalPages());
                        int page = NumberUtil.createInteger(value);
                        return pageRange.contains(page);
                    })
                    .build()
            )
            .build();
    }

    /**
     * Builds a Label wrapping a TextInput for index navigation search.
     *
     * @param itemHandler the item handler
     * @return the label containing the index search text input
     */
    public static @NotNull Label buildIndexSearchLabel(@NotNull ItemHandler<?> itemHandler) {
        return Label.builder()
            .withTitle("Go to Index")
            .withComponent(
                TextInput.builder()
                    .withStyle(TextInput.Style.SHORT)
                    .withSearchType(TextInput.SearchType.INDEX)
                    .withPlaceholder("Enter a number between 0 and %d.", itemHandler.getCachedFilteredItems().size())
                    .withValidator(value -> {
                        if (!NumberUtil.isCreatable(value))
                            return false;

                        Range<Integer> indexRange = Range.between(0, itemHandler.getCachedFilteredItems().size());
                        int index = NumberUtil.createInteger(value);
                        return indexRange.contains(index);
                    })
                    .build()
            )
            .build();
    }

    // --- Cached Page Components ---

    /**
     * Builds all pagination components for the given response, including page/subpage
     * select menus, item pagination buttons, editor menus, and button state updates.
     *
     * @param historyHandler the history handler to build components for
     * @param emojis the emoji resolver used for navigation button/option emojis
     * @return the built pagination components
     */
    public @NotNull ConcurrentList<TopLevelMessageComponent> buildCachedPageComponents(@NotNull HistoryHandler<? extends Page, String> historyHandler, @NotNull EmojiResolver emojis) {
        ConcurrentList<TopLevelMessageComponent> pageComponents = Concurrent.newList();
        Page currentPage = historyHandler.getCurrentPage();

        // Page List
        if (historyHandler.getItems().size() > 1 && !historyHandler.hasPageHistory()) {
            pageComponents.add(ActionRow.of(
                SelectMenu.builder()
                    .withPageType(SelectMenu.PageType.PAGE_SELECTOR)
                    .withPlaceholder("Select a page.")
                    .withPlaceholderShowingSelectedOption()
                    .withOptions(
                        historyHandler.getItems()
                            .stream()
                            .map(Page::getOption)
                            .collect(Concurrent.toList())
                    )
                    .onInteract(pageSelectionInteraction())
                    .build()
                    .updateSelected(historyHandler.getIdentifierHistory().getFirst())
            ));
        }

        // SubPage List - render the subpage selector (and its BACK option) whenever the current page has its
        // own subpages OR we have navigated into a subpage. A leaf subpage (no subpages of its own) still needs
        // the selector so it can render BACK and its sibling subpages; otherwise it is a navigation dead-end.
        if (historyHandler.hasChildNavigation() || historyHandler.hasPageHistory()) {
            HistoryHandler<?, String> pageHistory = currentPage.getHistoryHandler();

            if (pageHistory.getItems().notEmpty() || historyHandler.hasPageHistory()) {
                SelectMenu.StringMenu.Builder subPageBuilder = SelectMenu.builder()
                    .withPageType(SelectMenu.PageType.SUBPAGE_SELECTOR)
                    .withPlaceholder("Select a subpage.")
                    .withPlaceholderShowingSelectedOption()
                    .onInteract(subpageSelectionInteraction());

                if (historyHandler.hasPageHistory()) {
                    subPageBuilder.withOptions(
                        SelectMenu.Option.builder()
                            .withValue("BACK")
                            .withLabel("Back")
                            .withEmoji(emojis.getEmoji("ARROW_LEFT"))
                            .build()
                    );
                }

                HistoryHandler<?, String> subpageSource = pageHistory;

                if (pageHistory.getItems().isEmpty()) {
                    Optional<? extends Page> previousPage = historyHandler.getPreviousPage();

                    if (previousPage.isPresent())
                        subpageSource = previousPage.get().getHistoryHandler();
                }

                subPageBuilder.withOptions(
                    subpageSource.getItems()
                        .stream()
                        .filter(Page.class::isInstance)
                        .map(Page.class::cast)
                        .map(Page::getOption)
                        .collect(Concurrent.toList())
                );

                pageComponents.add(ActionRow.of(subPageBuilder.build()));
            }
        }

        if (currentPage.hasItems()) {
            // Item List - each button is built with its enabled/label state already applied, so there is no
            // post-build mutation of the (unmodifiable) action row.
            pageComponents.add(ActionRow.of(this.buildPaginationButtons(emojis, currentPage.getItemHandler())));
        }

        return pageComponents.toUnmodifiable();
    }

    /**
     * Builds all pagination buttons for the given item handler, each already carrying its enabled state
     * (prev/next/sort/filter) or page-index label (index), resolving emojis through the given resolver.
     *
     * @param emojis the emoji resolver
     * @param itemHandler the item handler whose paging state drives each button's enabled/label state
     * @return the state-applied pagination buttons
     */
    public @NotNull ConcurrentList<Button> buildPaginationButtons(@NotNull EmojiResolver emojis, @NotNull ItemHandler<?> itemHandler) {
        return Concurrent.newList(
            buildButton(Button.PageType.PREVIOUS, emojis.getEmoji("ARROW_LEFT")).mutate().setEnabled(itemHandler.hasPreviousItemPage()).build(),
            buildButton(Button.PageType.SORT, emojis.getEmoji("SORT")).mutate().setEnabled(itemHandler.getSortHandler().notEmpty()).build(),
            buildButton(Button.PageType.INDEX, emojis.getEmoji("SEARCH")).mutate().setEnabled(true).withLabel("%s / %s", itemHandler.getCurrentIndex(), itemHandler.getTotalPages()).build(),
            buildButton(Button.PageType.FILTER, emojis.getEmoji("FILTER")).mutate().setEnabled(itemHandler.getFilterHandler().notEmpty()).build(),
            buildButton(Button.PageType.NEXT, emojis.getEmoji("ARROW_RIGHT")).mutate().setEnabled(itemHandler.hasNextItemPage()).build()
        );
    }

    // --- Internal Helpers ---

    private static @NotNull Function<ButtonContext, Mono<Void>> getButtonInteraction(@NotNull Button.PageType pageType) {
        return switch (pageType) {
            case PREVIOUS -> previousPageInteraction();
            case SORT -> sortInteraction();
            case INDEX -> searchInteraction();
            case FILTER -> filterInteraction();
            case NEXT -> nextPageInteraction();
            case NONE -> __ -> Mono.empty();
        };
    }

}
