package dev.sbs.discordapi.response.page;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.collection.sort.SortOrder;
import dev.sbs.api.util.data.tuple.Triple;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.api.util.helper.NumberUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.component.layout.ActionRow;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;
import dev.sbs.discordapi.response.component.type.PreservableComponent;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.response.page.item.PageItem;
import dev.sbs.discordapi.response.page.item.SingletonFieldItem;
import dev.sbs.discordapi.util.exception.DiscordException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

public class Page extends PageItem implements Paging, SingletonFieldItem {

    @Getter private final @NotNull ConcurrentList<LayoutComponent<ActionComponent>> pageComponents;
    @Getter private final @NotNull Optional<String> content;
    @Getter private final @NotNull ConcurrentList<Page> pages;
    @Getter private final @NotNull ConcurrentList<Embed> embeds;
    @Getter private final @NotNull ConcurrentList<LayoutComponent<ActionComponent>> components;
    @Getter private final @NotNull ConcurrentList<Emoji> reactions;
    @Getter private final @NotNull ItemData<?> itemData;
    @Getter private int currentItemPage = 1;
    private ConcurrentList<PageItem> lastRenderedPageItems = Concurrent.newUnmodifiableList();

    protected Page(
        @NotNull String identifier,
        @NotNull Optional<SelectMenu.Option> option,
        @NotNull Optional<String> content,
        @NotNull ConcurrentList<Page> pages,
        @NotNull ConcurrentList<Embed> embeds,
        @NotNull ConcurrentList<LayoutComponent<ActionComponent>> components,
        @NotNull ConcurrentList<Emoji> reactions,
        @NotNull ItemData<?> itemData) {
        super(identifier, option, Type.PAGE, false);
        this.content = content;
        this.pages = Concurrent.newUnmodifiableList(pages);
        this.embeds = Concurrent.newUnmodifiableList(embeds);
        this.components = Concurrent.newUnmodifiableList(components);
        this.reactions = Concurrent.newUnmodifiableList(reactions);
        this.itemData = itemData;

        // Page Components
        ConcurrentList<LayoutComponent<ActionComponent>> pageComponents = Concurrent.newList();

        // SubPage List
        if (ListUtil.notEmpty(this.getPages())) {
            pageComponents.add(ActionRow.of(
                SelectMenu.builder()
                    .withPageType(SelectMenu.PageType.SUBPAGE)
                    .withPlaceholder("Select a subpage.")
                    .withOptions(
                        pages.stream()
                            //.filter(Page::doesNotHaveItems)
                            .map(Page::getOption)
                            .flatMap(Optional::stream)
                            .collect(Concurrent.toList())
                    )
                    .build()
            ));
        }

        // Item List
        if (this.getTotalItemPages() > 1) {
            for (int i = 1; i <= Button.PageType.getNumberOfRows(); i++) {
                int row = i;

                // Button Rows
                pageComponents.add(ActionRow.of(
                    Arrays.stream(Button.PageType.values())
                        .filter(pageType -> pageType.getRow() == row)
                        .map(Button.PageType::build)
                        .collect(Concurrent.toList())
                ));
            }
        }

        this.pageComponents = Concurrent.newUnmodifiableList(pageComponents);
        this.updatePagingComponents();
    }

    public static PageBuilder builder() {
        return new PageBuilder().withIdentifier(UUID.randomUUID().toString());
    }

    /**
     * Updates an existing paging {@link Button}.
     *
     * @param buttonBuilder The button to edit.
     */
    private <S> void editPageButton(@NotNull Function<Button, S> function, S value, Function<Button.ButtonBuilder, Button.ButtonBuilder> buttonBuilder) {
        this.pageComponents.forEach(layoutComponent -> layoutComponent.getComponents()
            .stream()
            .filter(Button.class::isInstance)
            .map(Button.class::cast)
            .filter(button -> Objects.equals(function.apply(button), value))
            .findFirst()
            .ifPresent(button -> layoutComponent.getComponents().set(
                layoutComponent.getComponents().indexOf(button),
                buttonBuilder.apply(button.mutate()).build()
            ))
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        Page page = (Page) o;

        return new EqualsBuilder()
            .append(this.getCurrentItemPage(), page.getCurrentItemPage())
            .append(this.getPageComponents(), page.getPageComponents())
            .append(this.getContent(), page.getContent())
            .append(this.getPages(), page.getPages())
            .append(this.getEmbeds(), page.getEmbeds())
            .append(this.getComponents(), page.getComponents())
            .append(this.getReactions(), page.getReactions())
            .append(this.getItemData(), page.getItemData())
            .build();
    }

    public final boolean doesHaveItems() {
        return ListUtil.notEmpty(this.getItemData().getFieldItems());
    }

    public final boolean doesNotHaveItems() {
        return !this.doesHaveItems();
    }

    /**
     * Finds an existing {@link ActionComponent}.
     *
     * @param tClass The component type to match.
     * @param function The method reference to match with.
     * @param value The value to match with.
     * @return The matching component, if it exists.
     */
    public <S, T extends ActionComponent> Optional<T> findComponent(@NotNull Class<T> tClass, @NotNull Function<T, S> function, S value) {
        return this.getComponents()
            .stream()
            .flatMap(layoutComponent -> layoutComponent.getComponents()
                .stream()
                .filter(tClass::isInstance)
                .map(tClass::cast)
                .filter(innerComponent -> Objects.equals(function.apply(innerComponent), value))
            )
            .findFirst();
    }

    /**
     * Finds an existing {@link Embed}.
     *
     * @param identifier The unique id of the embed to search for.
     * @return The matching embed, if it exists.
     */
    public Optional<Embed> findEmbed(@NotNull String identifier) {
        return this.getEmbeds()
            .stream()
            .filter(embed -> embed.getIdentifier().equals(identifier))
            .findFirst();
    }

    public static PageBuilder from(@NotNull Page page) {
        return new PageBuilder()
            .withIdentifier(page.getIdentifier())
            .withOption(page.getOption())
            .withContent(page.getContent())
            .withPages(page.getPages())
            .withEmbeds(page.getEmbeds())
            .withComponents(page.getComponents())
            .withReactions(page.getReactions())
            .withItemData(page.getItemData())
            .withItemData(page.getItemData());
    }

    @Override
    public Field getRenderField() {
        return Field.builder()
            .withName(this.getOption().map(SelectMenu.Option::getLabel))
            .withValue("Goto page.")
            .isInline()
            .build();
    }

    public final int getTotalItemPages() {
        return NumberUtil.roundUp((double) this.getItemData().getFieldItems().size() / this.getItemData().getAmountPerPage(), 1);
    }

    public final void gotoItemPage(int index) {
        this.currentItemPage = Math.min(this.getItemData().getFieldItems().size(), Math.max(1, index));
        this.lastRenderedPageItems = Concurrent.newUnmodifiableList();
        this.updatePagingComponents();
    }

    public final void gotoFirstItemPage() {
        this.gotoItemPage(1);
    }

    public final void gotoLastItemPage() {
        this.gotoItemPage(this.getTotalItemPages());
    }

    public final void gotoNextItemPage() {
        this.gotoItemPage(this.currentItemPage + 1);
    }

    public void gotoNextSorter() {
        this.getItemData().gotoNextSorter();

    }

    public final void gotoPreviousItemPage() {
        this.gotoItemPage(this.currentItemPage - 1);
    }

    public final boolean hasNextItemPage() {
        return this.currentItemPage < this.getTotalItemPages();
    }

    public final boolean hasPreviousItemPage() {
        return this.currentItemPage > 1;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(this.getPageComponents())
            .append(this.getContent())
            .append(this.getPages())
            .append(this.getEmbeds())
            .append(this.getComponents())
            .append(this.getReactions())
            .append(this.getItemData())
            .append(this.getCurrentItemPage())
            .build();
    }

    public PageBuilder mutate() {
        return from(this);
    }

    public final ConcurrentList<PageItem> getCachedPageItems() {
        if (ListUtil.isEmpty(this.lastRenderedPageItems)) {
            int startIndex = (this.getCurrentItemPage() - 1) * this.getItemData().getAmountPerPage();
            int endIndex = Math.min(startIndex + this.getItemData().getAmountPerPage(), ListUtil.sizeOf(this.getItemData().getFieldItems()));
            this.lastRenderedPageItems = Concurrent.newUnmodifiableList(this.getItemData().getTransformedFieldItems(startIndex, endIndex));
        }

        return this.lastRenderedPageItems;
    }

    private void updatePagingComponents() {
        // Enabled
        this.editPageButton(Button::getPageType, Button.PageType.FIRST, buttonBuilder -> buttonBuilder.setEnabled(this.hasPreviousItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.PREVIOUS, buttonBuilder -> buttonBuilder.setEnabled(this.hasPreviousItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.NEXT, buttonBuilder -> buttonBuilder.setEnabled(this.hasNextItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.LAST, buttonBuilder -> buttonBuilder.setEnabled(this.hasNextItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.SORT, buttonBuilder -> buttonBuilder.setEnabled(ListUtil.notEmpty(this.getItemData().getSorters())));

        // Labels
        this.editPageButton(
            Button::getPageType,
            Button.PageType.INDEX,
            buttonBuilder -> buttonBuilder.withLabel(FormatUtil.format(
                "{0} / {1}", this.getCurrentItemPage(), this.getTotalItemPages()
            ))
        );

        this.editPageButton(
            Button::getPageType,
            Button.PageType.SORT,
            buttonBuilder -> buttonBuilder.withLabel(FormatUtil.format(
                "Sort: {0}", this.getItemData()
                    .getCurrentSorter()
                    .map(sorter -> sorter.getOption().getLabel())
                    .orElse("N/A")
            ))
        );
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    public static class PageBuilder implements Builder<Page> {

        private String identifier;
        private final ConcurrentList<Page> pages = Concurrent.newList();
        private final ConcurrentList<Embed> embeds = Concurrent.newList();
        private final ConcurrentList<LayoutComponent<ActionComponent>> components = Concurrent.newList();
        private final ConcurrentList<Emoji> reactions = Concurrent.newList();
        private Optional<String> content = Optional.empty();
        private Optional<SelectMenu.Option> option = Optional.empty();
        private ItemData<?> itemData = ItemData.builder(PageItem.class).build();

        /**
         * Clear all but preservable components from {@link Page}.
         */
        public PageBuilder clearComponents() {
            return this.clearComponents(false);
        }

        /**
         * Clear all but preservable components from {@link Page}.
         *
         * @param recursive True to recursively clear components.
         */
        public PageBuilder clearComponents(boolean recursive) {
            return this.clearComponents(recursive, true);
        }

        /**
         * Clear all components from {@link Page}.
         *
         * @param recursive True to recursively clear components.
         * @param enforcePreserve True to leave preservable components.
         */
        public PageBuilder clearComponents(boolean recursive, boolean enforcePreserve) {
            // Remove Possibly Preserved Components
            this.components.stream()
                .filter(layoutComponent -> !enforcePreserve || layoutComponent.notPreserved())
                .forEach(layoutComponent -> layoutComponent.getComponents()
                    .stream()
                    .filter(PreservableComponent.class::isInstance)
                    .map(PreservableComponent.class::cast)
                    .filter(component -> !enforcePreserve || component.notPreserved())
                    .forEach(component -> layoutComponent.getComponents().remove(component))
                );

            if (recursive)
                this.pages.forEach(page -> this.editPage(page.mutate().clearComponents(true, enforcePreserve).build()));

            // Remove Empty Layout Components
            this.components.removeIf(layoutComponent -> layoutComponent.getComponents().isEmpty());
            return this;
        }

        /**
         * Clear all pages from the {@link Page}.
         */
        public PageBuilder clearPages() {
            this.pages.clear();
            return this;
        }

        /**
         * Updates an existing {@link ActionComponent}.
         *
         * @param actionComponent The component to edit.
         */
        public PageBuilder editComponent(@NotNull ActionComponent actionComponent) {
            this.components.forEach(layoutComponent -> layoutComponent.getComponents()
                .stream()
                .filter(actionComponent.getClass()::isInstance)
                .map(actionComponent.getClass()::cast)
                .filter(innerComponent -> innerComponent.getIdentifier().equals(actionComponent.getIdentifier()))
                .findFirst()
                .ifPresent(innerComponent -> layoutComponent.getComponents().set(
                    layoutComponent.getComponents().indexOf(innerComponent),
                    actionComponent
                ))
            );

            return this;
        }

        /**
         * Edits an existing {@link Embed}.
         *
         * @param identifier The identifier of the embed to search for.
         * @param embedBuilder The embed builder to edit with.
         */
        public PageBuilder editEmbed(@NotNull String identifier, @NotNull Function<Embed.EmbedBuilder, Embed.EmbedBuilder> embedBuilder) {
            this.findEmbed(identifier).ifPresent(embed -> {
                Embed editedEmbed = embedBuilder.apply(embed.mutate()).build();

                // Locate and Update Existing Embed
                for (int i = 0; i < this.embeds.size(); i++) {
                    if (this.embeds.get(i).getIdentifier().equals(identifier)) {
                        this.embeds.set(i, editedEmbed);
                        break;
                    }
                }
            });

            return this;
        }

        /**
         * Edits an existing {@link Page} at the given index.
         *
         * @param pageBuilder The page builder to edit with.
         */
        public PageBuilder editPage(@NotNull Function<PageBuilder, PageBuilder> pageBuilder) {
            return this.editPage(0, pageBuilder);
        }

        /**
         * Edits an existing {@link Page} at the given index.
         *
         * @param index The page index to edit.
         * @param pageBuilder The page builder to edit with.
         */
        public PageBuilder editPage(int index, @NotNull Function<PageBuilder, PageBuilder> pageBuilder) {
            if (index < this.pages.size())
                this.pages.set(index, pageBuilder.apply(this.pages.get(index).mutate()).build());

            return this;
        }

        /**
         * Updates an existing {@link Page}.
         *
         * @param page The page to edit.
         */
        public PageBuilder editPage(@NotNull Page page) {
            this.pages.stream()
                .filter(existingPage -> existingPage.getIdentifier().equals(page.getIdentifier()))
                .findFirst()
                .ifPresent(existingPage -> this.pages.set(this.pages.indexOf(existingPage), page));

            return this;
        }

        /**
         * Finds an existing {@link Embed}.
         *
         * @param identifier The identifier of the embed to search for.
         * @return The matching embed, if it exists.
         */
        public Optional<Embed> findEmbed(@NotNull String identifier) {
            return this.embeds.stream()
                .filter(embed -> embed.getIdentifier().equals(identifier))
                .findFirst();
        }

        /**
         * Finds an existing {@link ActionComponent}.
         *
         * @param tClass The component type to match.
         * @param function The method reference to match with.
         * @param value The value to match with.
         * @return The matching component, if it exists.
         */
        public <S, A extends ActionComponent> Optional<A> findComponent(@NotNull Class<A> tClass, @NotNull Function<A, S> function, S value) {
            return this.components.stream()
                .flatMap(layoutComponent -> layoutComponent.getComponents()
                    .stream()
                    .filter(tClass::isInstance)
                    .map(tClass::cast)
                    .filter(innerComponent -> Objects.equals(function.apply(innerComponent), value))
                )
                .findFirst();
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the {@link Page}.
         *
         * @param components Variable number of layout components to add.
         */
        @SuppressWarnings("all")
        public PageBuilder withComponents(@NotNull LayoutComponent<ActionComponent>... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the {@link Page}.
         *
         * @param components Collection of layout components to add.
         */
        public PageBuilder withComponents(@NotNull Iterable<LayoutComponent<ActionComponent>> components) {
            components.forEach(this.components::add);
            return this;
        }

        /**
         * Sets the content text to add to the {@link Page}.
         *
         * @param content The text to add to the page.
         */
        public PageBuilder withContent(@Nullable String content) {
            return this.withContent(Optional.ofNullable(content));
        }

        /**
         * Sets the content text to add to the {@link Page}.
         *
         * @param content The text to add to the page.
         */
        public PageBuilder withContent(@NotNull Optional<String> content) {
            this.content = content;
            return this;
        }

        /**
         * Add {@link Embed Embeds} to the {@link Page}.
         *
         * @param embeds Variable number of embeds to add.
         */
        public PageBuilder withEmbeds(@NotNull Embed... embeds) {
            return this.withEmbeds(Arrays.asList(embeds));
        }

        /**
         * Add {@link Embed Embeds} to the {@link Page}.
         *
         * @param embeds Collection of embeds to add.
         */
        public PageBuilder withEmbeds(@NotNull Iterable<Embed> embeds) {
            embeds.forEach(this.embeds::add);
            return this;
        }

        /**
         * Overrides the default identifier of the {@link Page}.
         *
         * @param identifier The identifier to use.
         * @param objects The objects used to format the identifier.
         */
        public PageBuilder withIdentifier(@NotNull String identifier, @NotNull Object... objects) {
            this.identifier = FormatUtil.format(identifier, objects);
            return this;
        }

        /**
         * Sets the item data to be used with the {@link Page}.
         *
         * @param itemData The item data for the page.
         */
        public PageBuilder withItemData(@NotNull ItemData<?> itemData) {
            this.itemData = itemData;
            return this;
        }

        /**
         * Define the {@link SelectMenu.Option} data of the {@link Page}.
         *
         * @param option The option to add.
         */
        public PageBuilder withOption(@Nullable SelectMenu.Option option) {
            return this.withOption(Optional.ofNullable(option));
        }

        /**
         * Define the {@link SelectMenu.Option} data of the {@link Page}.
         *
         * @param option The option to add.
         */
        public PageBuilder withOption(@NotNull Optional<SelectMenu.Option> option) {
            this.option = option;
            return this;
        }

        /**
         * Add sub {@link Page Pages} to the {@link Page}.
         *
         * @param subPages Variable number of pages to add.
         */
        public PageBuilder withPages(@NotNull Page... subPages) {
            return this.withPages(Arrays.asList(subPages));
        }

        /**
         * Add sub {@link Page Pages} to the {@link Page}.
         *
         * @param subPages Collection of pages to add.
         */
        public PageBuilder withPages(@NotNull Iterable<Page> subPages) {
            subPages.forEach(this.pages::add);
            return this;
        }

        /**
         * Sets the reactions to add to the {@link Page}.
         *
         * @param reactions The reactions to add to the response.
         */
        public PageBuilder withReactions(@NotNull Emoji... reactions) {
            return this.withReactions(Arrays.asList(reactions));
        }

        /**
         * Sets the reactions to add to the {@link Page}.
         *
         * @param reactions The reactions to add to the response.
         */
        public PageBuilder withReactions(@NotNull Iterable<Emoji> reactions) {
            reactions.forEach(this.reactions::add);
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link Page}.
         */
        @Override
        public Page build() {
            return new Page(
                this.identifier,
                this.option,
                this.content,
                this.pages,
                this.embeds,
                this.components,
                this.reactions,
                this.itemData
            );
        }

    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @SuppressWarnings("all")
    public static class ItemData<T> {

        @Getter private final Class<T> type;
        @Getter private final ConcurrentList<T> fieldItems;
        @Getter private final ConcurrentList<PageItem> items;
        @Getter private final Function<Stream<T>, Stream<? extends SingletonFieldItem>> transformer;
        @Getter private final ConcurrentList<Sorter<T>> sorters;
        @Getter private final @NotNull SortOrder order;
        @Getter private final @NotNull PageItem.Style style;
        @Getter private final int amountPerPage;
        @Getter private final @NotNull Optional<Triple<String, String, String>> columnNames;
        @Getter private int currentSorterIndex = -1;

        public static <T> Builder<T> builder(@NotNull Class<T> type) {
            return new Builder<>(type);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ItemData<?> itemData = (ItemData<?>) o;

            return new EqualsBuilder()
                .append(this.getAmountPerPage(), itemData.getAmountPerPage())
                .append(this.getType(), itemData.getType())
                .append(this.getFieldItems(), itemData.getFieldItems())
                .append(this.getItems(), itemData.getItems())
                .append(this.getTransformer(), itemData.getTransformer())
                .append(this.getSorters(), itemData.getSorters())
                .append(this.getOrder(), itemData.getOrder())
                .append(this.getStyle(), itemData.getStyle())
                .append(this.getColumnNames(), itemData.getColumnNames())
                .build();
        }

        public static <T> Builder<T> from(@NotNull ItemData<T> itemData) {
            return new Builder<>(itemData.getType())
                .withFieldItems(itemData.getFieldItems())
                .withItems(itemData.getItems())
                .withTransformer(itemData.getTransformer())
                .withFilters(itemData.getSorters())
                .withOrder(itemData.getOrder())
                .withStyle(itemData.getStyle())
                .withAmountPerPage(itemData.getAmountPerPage())
                .withColumnNames(itemData.getColumnNames());
        }

        public Optional<Sorter<T>> getCurrentSorter() {
            return Optional.ofNullable(this.getCurrentSorterIndex() > -1 ? this.getSorters().get(this.getCurrentSorterIndex()) : null);
        }

        public final ConcurrentList<PageItem> getTransformedFieldItems() {
            return this.getTransformedFieldItems(0, ListUtil.sizeOf(this.getFieldItems()));
        }

        public final ConcurrentList<PageItem> getTransformedFieldItems(int startIndex, int endIndex) {
            return this.getTransformer()
                .apply(
                    this.getCurrentSorter()
                        .map(sorter -> sorter.apply(this.getFieldItems()))
                        .orElse(this.getFieldItems())
                        .subList(startIndex, endIndex)
                        .stream()
                )
                .filter(PageItem.class::isInstance)
                .map(PageItem.class::cast)
                .collect(Concurrent.toList());
        }

        public void gotoNextSorter() {
            this.currentSorterIndex++;

            if (this.currentSorterIndex >= ListUtil.sizeOf(this.getSorters()))
                this.currentSorterIndex = -1;
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                .append(this.getType())
                .append(this.getFieldItems())
                .append(this.getItems())
                .append(this.getTransformer())
                .append(this.getSorters())
                .append(this.getOrder())
                .append(this.getStyle())
                .append(this.getAmountPerPage())
                .append(this.getColumnNames())
                .build();
        }

        public Builder<T> mutate() {
            return from(this);
        }

        @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
        public static class Sorter<T> implements Function<ConcurrentList<T>, ConcurrentList<T>> {

            @Getter private final @NotNull SelectMenu.Option option;
            @Getter private final @NotNull ConcurrentMap<Comparator<? extends T>, SortOrder> comparators;
            @Getter private final @NotNull SortOrder order;

            @Override
            public ConcurrentList<T> apply(ConcurrentList<T> list) {
                ConcurrentList<T> copy = Concurrent.newList(list);

                copy.sort((o1, o2) -> {
                    Iterator<Map.Entry<Comparator<? extends T>, SortOrder>> iterator = this.getComparators().iterator();
                    Map.Entry<Comparator<? extends T>, SortOrder> entry = iterator.next();
                    Comparator comparator = entry.getKey();

                    if (entry.getValue() == SortOrder.DESCENDING)
                        comparator = comparator.reversed();

                    while (iterator.hasNext()) {
                        entry = iterator.next();
                        comparator = comparator.thenComparing(entry.getKey());

                        if (entry.getValue() == SortOrder.DESCENDING)
                            comparator = comparator.reversed();
                    }

                    return this.getOrder() == SortOrder.ASCENDING ? comparator.compare(o1, o2) : comparator.compare(o2, o1);
                });

                return copy;
            }

            public static <T> Builder<T> builder() {
                return new Builder<>();
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Sorter<?> sorter = (Sorter<?>) o;

                return new EqualsBuilder()
                    .append(this.getOption(), sorter.getOption())
                    .append(this.getComparators(), sorter.getComparators())
                    .append(this.getOrder(), sorter.getOrder())
                    .build();
            }

            public static <T> Builder<T> from(@NotNull Sorter<T> sorter) {
                return new Builder<>()
                    .withOption(sorter.getOption())
                    .withComparators(sorter.getComparators())
                    .withOrder(sorter.getOrder());
            }

            @Override
            public int hashCode() {
                return new HashCodeBuilder()
                    .append(this.getOption())
                    .append(this.getComparators())
                    .append(this.getOrder())
                    .build();
            }

            public Builder<T> mutate() {
                return from(this);
            }

            @NoArgsConstructor(access = AccessLevel.PRIVATE)
            public static class Builder<T> implements dev.sbs.api.util.builder.Builder<Sorter<T>> {

                private final SelectMenu.Option.OptionBuilder optionBuilder = SelectMenu.Option.builder().withIdentifier(UUID.randomUUID().toString());
                private final ConcurrentMap<Comparator<? extends T>, SortOrder> comparators = Concurrent.newMap();
                private SortOrder order = SortOrder.DESCENDING;

                /**
                 * Add custom comparators for the {@link PageItem FieldItems}.
                 *
                 * @param comparators A variable amount of comparators.
                 */
                public Builder<T> withComparators(@NotNull Comparator<? extends T>... comparators) {
                    return this.withComparators(Arrays.asList(comparators));
                }

                /**
                 * Add custom comparators for the {@link PageItem FieldItems}.
                 *
                 * @param order How the comparators are sorted.
                 * @param comparators A variable amount of comparators.
                 */
                public Builder<T> withComparators(@NotNull SortOrder order, @NotNull Comparator<? extends T>... comparators) {
                    return this.withComparators(Arrays.asList(comparators));
                }

                /**
                 * Add custom sort functions for the {@link PageItem FieldItems}.
                 *
                 * @param comparators A variable amount of comparators.
                 */
                public Builder<T> withComparators(@NotNull Iterable<Comparator<? extends T>> comparators) {
                    return this.withComparators(SortOrder.DESCENDING, comparators);
                }

                /**
                 * Add custom sort functions for the {@link PageItem FieldItems}.
                 *
                 * @param order How the comparators are sorted.
                 * @param comparators A variable amount of comparators.
                 */
                public Builder<T> withComparators(@NotNull SortOrder order, @NotNull Iterable<Comparator<? extends T>> comparators) {
                    comparators.forEach(comparator -> this.comparators.put(comparator, order));
                    return this;
                }

                /**
                 * Sets the description of the {@link Sorter}.
                 *
                 * @param description The description to use.
                 * @param objects The objects used to format the description.
                 */
                public Builder<T> withDescription(@Nullable String description, @NotNull Object... objects) {
                    return this.withDescription(FormatUtil.formatNullable(description, objects));
                }

                /**
                 * Sets the description of the {@link Sorter}.
                 *
                 * @param description The description to use.
                 */
                public Builder<T> withDescription(@NotNull Optional<String> description) {
                    this.optionBuilder.withDescription(description);
                    return this;
                }

                /**
                 * Sets the emoji of the {@link Sorter}.
                 * <br><br>
                 * This is used for the {@link Button#getEmoji()}.
                 *
                 * @param emoji The emoji to use.
                 */
                public Builder<T> withEmoji(@Nullable Emoji emoji) {
                    return this.withEmoji(Optional.ofNullable(emoji));
                }

                /**
                 * Sets the emoji of the {@link Sorter}.
                 * <br><br>
                 * This is used for the {@link Button#getEmoji()}.
                 *
                 * @param emoji The emoji to use.
                 */
                public Builder<T> withEmoji(@NotNull Optional<Emoji> emoji) {
                    this.optionBuilder.withEmoji(emoji);
                    return this;
                }

                /**
                 * Add custom sort functions for the {@link PageItem FieldItems}.
                 *
                 * @param functions A variable amount of sort functions.
                 */
                public Builder<T> withFunctions(@NotNull Function<T, ? extends Comparable>... functions) {
                    return this.withFunctions(SortOrder.DESCENDING, functions);
                }

                /**
                 * Add custom sort functions for the {@link PageItem FieldItems}.
                 *
                 * @param functions A variable amount of sort functions.
                 * @param order How the comparators are sorted.
                 */
                public Builder<T> withFunctions(@NotNull SortOrder order, @NotNull Function<T, ? extends Comparable>... functions) {
                    return this.withFunctions(order, Arrays.asList(functions));
                }

                /**
                 * Add custom sort functions for the {@link PageItem FieldItems}.
                 *
                 * @param functions A collection of sort functions.
                 */
                public Builder<T> withFunctions(@NotNull Iterable<Function<T, ? extends Comparable>> functions) {
                    return this.withFunctions(SortOrder.DESCENDING, functions);
                }

                /**
                 * Add custom sort functions for the {@link PageItem FieldItems}.
                 *
                 * @param functions A collection of sort functions.
                 * @param order How the comparators are sorted.
                 */
                public Builder<T> withFunctions(@NotNull SortOrder order, @NotNull Iterable<Function<T, ? extends Comparable>> functions) {
                    functions.forEach(function -> this.comparators.put(Comparator.comparing(function), order));
                    return this;
                }

                /**
                 * Overrides the default identifier of the {@link Sorter}.
                 * <br><br>
                 * This is used for the {@link Sorter}.
                 *
                 * @param identifier The identifier to use.
                 * @param objects The objects used to format the value.
                 */
                public Builder<T> withIdentifier(@NotNull String identifier, @NotNull Object... objects) {
                    this.optionBuilder.withIdentifier(identifier, objects);
                    return this;
                }

                /**
                 * Sets the label of the {@link Sorter}.
                 * <br><br>
                 * This is used for the {@link Button}.
                 *
                 * @param label The label of the field item.
                 * @param objects The objects used to format the label.
                 */
                public Builder withLabel(@NotNull String label, @NotNull Object... objects) {
                    this.optionBuilder.withLabel(label, objects);
                    return this;
                }

                public Builder withOption(@NotNull SelectMenu.Option option) {
                    return this.withIdentifier(option.getIdentifier())
                        .withDescription(option.getDescription())
                        .withEmoji(option.getEmoji())
                        .withLabel(option.getLabel());
                }

                /**
                 * Sets the sort order for the {@link PageItem PageItems}.
                 * <br><br>
                 * Descending - Highest to Lowest (Default)<br>
                 * Ascending - Lowest to Highest
                 *
                 * @param order The order to sort the items in.
                 */
                public Builder<T> withOrder(@NotNull SortOrder order) {
                    this.order = order;
                    return this;
                }

                @Override
                public Sorter<T> build() {
                    if (ListUtil.isEmpty(this.comparators))
                        throw SimplifiedException.of(DiscordException.class)
                            .withMessage("Comparators cannot be empty!")
                            .build();

                    return new Sorter<>(
                        this.optionBuilder.build(),
                        Concurrent.newUnmodifiableMap(this.comparators),
                        this.order
                    );
                }

            }

        }

        @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
        public static class Builder<T> implements dev.sbs.api.util.builder.Builder<ItemData<T>> {

            private final Class<T> type;
            private final ConcurrentList<T> fieldItems = Concurrent.newList();
            private final ConcurrentList<PageItem> items = Concurrent.newList();
            private Function<Stream<T>, Stream<? extends SingletonFieldItem>> transformer;
            private final ConcurrentList<Sorter<T>> filters = Concurrent.newList();
            private SortOrder order = SortOrder.DESCENDING;
            private PageItem.Style style = Style.FIELD_INLINE;
            private int amountPerPage = 12;
            private Optional<Triple<String, String, String>> columnNames = Optional.empty();

            /**
             * Clear all items from the {@link Page}.
             */
            public Builder<T> clearItems() {
                this.fieldItems.clear();
                return this;
            }

            /**
             * Sets the amount of {@link PageItem PageItems} to render per {@link Page}.
             * <br><br>
             * Defaults to 12.<br>
             * Minimum required is 1.<br>
             * Maximum allowed is 24.
             *
             * @param amountPerPage Number of items to render.
             */
            public Builder<T> withAmountPerPage(int amountPerPage) {
                this.amountPerPage = NumberUtil.ensureRange(amountPerPage, 1, 24);
                return this;
            }

            /**
             * Sets the field names to use when rendering {@link PageItem PageItems}.
             *
             * @param columnOne The field name for page items in column 1.
             * @param columnTwo The field name for page items in column 2.
             * @param columnThree The field name for page items in column 3.
             */
            public Builder<T> withColumnNames(@Nullable String columnOne, @Nullable String columnTwo, @Nullable String columnThree) {
                return this.withColumnNames(Triple.of(columnOne, columnTwo, columnThree));
            }

            /**
             * Sets the field names to use when rendering {@link PageItem PageItems}.
             *
             * @param fieldNames The field names for page items.
             */
            public Builder<T> withColumnNames(@Nullable Triple<String, String, String> fieldNames) {
                return this.withColumnNames(Optional.ofNullable(fieldNames));
            }

            /**
             * Sets the field names to use when rendering {@link PageItem PageItems}.
             *
             * @param fieldNames The field names for page items.
             */
            public Builder<T> withColumnNames(@NotNull Optional<Triple<String, String, String>> fieldNames) {
                this.columnNames = fieldNames;
                return this;
            }

            /**
             * Add {@link SingletonFieldItem FieldItems} to the {@link Page} item list.
             *
             * @param fieldItems Variable number of field items to add.
             */
            public Builder<T> withFieldItems(@NotNull T... fieldItems) {
                return this.withFieldItems(Arrays.asList(fieldItems));
            }

            /**
             * Add {@link SingletonFieldItem FieldItems} to the {@link Page} item list.
             *
             * @param fieldItems Collection of field items to add.
             */
            public Builder<T> withFieldItems(@NotNull Iterable<T> fieldItems) {
                fieldItems.forEach(this.fieldItems::add);
                return this;
            }

            /**
             * Add custom sort filter for the {@link PageItem FieldItems}.
             *
             * @param comparators A variable amount of filters.
             */
            public Builder<T> withFilters(@NotNull Sorter<T>... sorters) {
                return this.withFilters(Arrays.asList(sorters));
            }

            /**
             * Add custom sort filters for the {@link PageItem FieldItems}.
             *
             * @param comparators A variable amount of filters.
             */
            public Builder<T> withFilters(@NotNull Iterable<Sorter<T>> filters) {
                filters.forEach(this.filters::add);
                return this;
            }

            /**
             * Add {@link PageItem PageItems} to the {@link Page} item list.
             *
             * @param items Variable number of items to add.
             */
            public Builder<T> withItems(@NotNull PageItem... items) {
                return this.withItems(Arrays.asList(items));
            }

            /**
             * Add {@link PageItem PageItems} to the {@link Page} item list.
             *
             * @param items Variable number of items to add.
             */
            public Builder<T> withItems(@NotNull Iterable<PageItem> items) {
                items.forEach(this.items::add);
                return this;
            }

            /**
             * Sets the sort order for the {@link PageItem PageItems}.
             * <br><br>
             * Descending - Highest to Lowest (Default)<br>
             * Ascending - Lowest to Highest
             *
             * @param order The order to sort the items in.
             */
            public Builder<T> withOrder(@NotNull SortOrder order) {
                this.order = order;
                return this;
            }

            /**
             * Sets the transformer used to render the {@link SingletonFieldItem SingletonFieldItems}.
             *
             * @param transformer How to render {@link T}.
             */
            public Builder<T> withTransformer(@NotNull Function<Stream<T>, Stream<? extends SingletonFieldItem>> transformer) {
                this.transformer = transformer;
                return this;
            }

            /**
             * Sets the render style for {@link PageItem PageItems}.
             *
             * @param itemStyle The page item style.
             */
            public Builder<T> withStyle(@NotNull PageItem.Style itemStyle) {
                this.style = itemStyle;
                return this;
            }

            @Override
            public ItemData<T> build() {
                return new ItemData<T>(
                    this.type,
                    Concurrent.newUnmodifiableList(this.fieldItems),
                    Concurrent.newUnmodifiableList(this.items),
                    this.transformer,
                    this.filters,
                    this.order,
                    this.style,
                    this.amountPerPage,
                    this.columnNames
                );
            }

        }

    }

}
