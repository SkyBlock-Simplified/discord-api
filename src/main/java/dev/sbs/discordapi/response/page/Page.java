package dev.sbs.discordapi.response.page;

import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
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
import dev.sbs.discordapi.response.page.item.PageItem;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class Page extends PageItem implements Paging {

    @Getter private final ConcurrentList<LayoutComponent<ActionComponent>> pageComponents;
    @Getter private final ConcurrentList<Page> pages;
    @Getter private final ConcurrentList<LayoutComponent<ActionComponent>> components;
    @Getter private final ConcurrentList<Emoji> reactions;
    @Getter private final ConcurrentList<Embed> embeds;
    @Getter private final ConcurrentList<PageItem> items;
    @Getter private final Optional<String> content;
    @Getter private final Optional<SelectMenu.Option> option;
    @Getter private final PageItem.Style itemStyle;
    @Getter private final Optional<Triple<String, String, String>> fieldNames;
    @Getter private final int itemsPerPage;
    @Getter private int currentItemPage = 1;

    protected Page(
        @NotNull String identifier,
        @NotNull ConcurrentList<LayoutComponent<ActionComponent>> components,
        @NotNull ConcurrentList<Emoji> reactions,
        @NotNull ConcurrentList<Embed> embeds,
        @NotNull ConcurrentList<Page> pages,
        @NotNull ConcurrentList<PageItem> items,
        @NotNull Optional<String> content,
        @NotNull Optional<SelectMenu.Option> option,
        @NotNull PageItem.Style itemStyle,
        @NotNull Optional<Triple<String, String, String>> fieldNames,
        int itemsPerPage) {
        super(identifier, option, Type.PAGE, true);
        this.pages = pages;
        this.components = components;
        this.reactions = reactions;
        this.embeds = embeds;
        this.items = items;
        this.option = option;
        this.content = content;
        this.itemStyle = itemStyle;
        this.fieldNames = fieldNames;
        this.itemsPerPage = itemsPerPage;

        // Page Components
        ConcurrentList<LayoutComponent<ActionComponent>> pageComponents = Concurrent.newList();

        // SubPage List
        if (ListUtil.notEmpty(pages)) {
            pageComponents.add(ActionRow.of(
                SelectMenu.builder()
                    .withPageType(SelectMenu.PageType.SUBPAGE)
                    .withPlaceholder("Select a subpage.")
                    .withOptions(
                        pages.stream()
                            .filter(page -> !page.isItemSelector() || ListUtil.notEmpty(page.getItems()))
                            .map(Page::getOption)
                            .flatMap(Optional::stream)
                            .collect(Concurrent.toList())
                    )
                    .build()
            ));
        }

        if (NumberUtil.round((double) items.size() / itemsPerPage) > 1) {
            // Item List
            pageComponents.add(ActionRow.of(
                Arrays.stream(Button.PageType.values())
                    .filter(Button.PageType::isForItemList)
                    .map(Button.PageType::build)
                    .collect(Concurrent.toList())
            ));

            // Item Selector
            /*pageComponents.add(ActionRow.of(
                SelectMenu.builder()
                    .isPageSelector(true)
                    .withPlaceholder("Select an item.")
                    .withOptions(
                        items.stream()
                            .map(PageItem::getOption)
                            .collect(Concurrent.toList())
                    )
                    .build()
            ));*/
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

        Page page = (Page) o;

        return new EqualsBuilder()
            .append(this.getItemStyle(), page.getItemStyle())
            .append(this.getFieldNames(), page.getFieldNames())
            .append(this.getItemsPerPage(), page.getItemsPerPage())
            .append(this.getCurrentItemPage(), page.getCurrentItemPage())
            .append(this.getIdentifier(), page.getIdentifier())
            .append(this.getPages(), page.getPages())
            .append(this.getPageComponents(), page.getPageComponents())
            .append(this.getComponents(), page.getComponents())
            .append(this.getReactions(), page.getReactions())
            .append(this.getEmbeds(), page.getEmbeds())
            .append(this.getItems(), page.getItems())
            .append(this.getContent(), page.getContent())
            .append(this.getOption(), page.getOption())
            .build();
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
            .withComponents(page.getComponents())
            .withReactions(page.getReactions())
            .withEmbeds(page.getEmbeds())
            .withPages(page.getPages())
            .withItems(page.getItems())
            .withContent(page.getContent())
            .withOption(page.getOption())
            .withItemStyle(page.getItemStyle())
            .withColumnNames(page.getFieldNames())
            .withItemsPerPage(page.getItemsPerPage());
    }

    @Override
    public String getFieldValue(@NotNull Style itemStyle, @NotNull Column column) {
        return null; // TODO: NOT IMPLEMENTED
    }

    // Item Paging
    public final Optional<PageItem> getItem(int index) {
        return this.getCurrentItemPage() < this.getPages().size() ? Optional.of(this.getItems().get(index)) : Optional.empty();
    }

    public final int getTotalItemPages() {
        return NumberUtil.roundUp((double) this.items.size() / this.getItemsPerPage(), 1);
    }

    public final void gotoItemPage(int index) {
        this.currentItemPage = Math.min(this.items.size(), Math.max(1, index));
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
            .append(this.getIdentifier())
            .append(this.getPages())
            .append(this.getPageComponents())
            .append(this.getComponents())
            .append(this.getReactions())
            .append(this.getEmbeds())
            .append(this.getItems())
            .append(this.getContent())
            .append(this.getOption())
            .append(this.getItemStyle())
            .append(this.getItemsPerPage())
            .append(this.getFieldNames())
            .append(this.getCurrentItemPage())
            .build();
    }

    public final boolean isItemSelector() {
        return ListUtil.notEmpty(this.items);
    }

    public PageBuilder mutate() {
        return from(this);
    }

    private void updatePagingComponents() {
        this.editPageButton(Button::getPageType, Button.PageType.FIRST, buttonBuilder -> buttonBuilder.setEnabled(this.hasPreviousItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.PREVIOUS, buttonBuilder -> buttonBuilder.setEnabled(this.hasPreviousItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.NEXT, buttonBuilder -> buttonBuilder.setEnabled(this.hasNextItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.LAST, buttonBuilder -> buttonBuilder.setEnabled(this.hasNextItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.INDEX, buttonBuilder -> buttonBuilder.withLabel(FormatUtil.format("{0} / {1}", this.getCurrentItemPage(), this.getTotalItemPages())));
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    public static class PageBuilder implements Builder<Page> {

        private String identifier;
        private final ConcurrentList<Embed> embeds = Concurrent.newList();
        private final ConcurrentList<LayoutComponent<ActionComponent>> components = Concurrent.newList();
        private final ConcurrentList<Emoji> reactions = Concurrent.newList();
        private final ConcurrentList<Page> pages = Concurrent.newList();
        private final ConcurrentList<PageItem> items = Concurrent.newList();
        private Optional<String> content = Optional.empty();
        private Optional<SelectMenu.Option> option = Optional.empty();
        private PageItem.Style itemStyle = PageItem.Style.FIELD;
        private Optional<Triple<String, String, String>> fieldNames = Optional.empty();
        private int itemsPerPage = 12;

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
         * Clear all sub pages from {@link Page}.
         */
        public PageBuilder clearSubPages() {
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
         * Sets the field names to use when rendering {@link PageItem PageItems}.
         *
         * @param columnOne The field name for page items in column 1.
         * @param columnTwo The field name for page items in column 2.
         * @param columnThree The field name for page items in column 3.
         */
        public PageBuilder withColumnNames(@Nullable String columnOne, @Nullable String columnTwo, @Nullable String columnThree) {
            return this.withColumnNames(Triple.of(columnOne, columnTwo, columnThree));
        }

        /**
         * Sets the field names to use when rendering {@link PageItem PageItems}.
         *
         * @param fieldNames The field names for page items.
         */
        public PageBuilder withColumnNames(@Nullable Triple<String, String, String> fieldNames) {
            return this.withColumnNames(Optional.ofNullable(fieldNames));
        }

        /**
         * Sets the field names to use when rendering {@link PageItem PageItems}.
         *
         * @param fieldNames The field names for page items.
         */
        public PageBuilder withColumnNames(@NotNull Optional<Triple<String, String, String>> fieldNames) {
            this.fieldNames = fieldNames;
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
         * Add a {@link PageItem} to the {@link Page}.
         *
         * @param pageItems Variable number of page items to add.
         */
        public <E extends PageItem> PageBuilder withItems(@NotNull E... pageItems) {
            return this.withItems(Arrays.asList(pageItems));
        }

        /**
         * Add a sub {@link PageItem} to the {@link Page}.
         *
         * @param pageItems Collection of page items to add.
         */
        public <E extends PageItem> PageBuilder withItems(@NotNull Iterable<E> pageItems) {
            pageItems.forEach(this.items::add);
            return this;
        }

        /**
         * Sets the number of {@link PageItem PageItems} to render per {@link Page}.
         * <br><br>
         * Defaults to 3. Minimum required is 1.
         *
         * @param itemsPerPage Number of items to render.
         */
        public PageBuilder withItemsPerPage(int itemsPerPage) {
            this.itemsPerPage = Math.max(1, itemsPerPage);
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
         * Sets the render style for {@link PageItem PageItems}.
         *
         * @param itemStyle The page item style.
         */
        public PageBuilder withItemStyle(@NotNull PageItem.Style itemStyle) {
            this.itemStyle = itemStyle;
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
                Concurrent.newUnmodifiableList(this.components),
                Concurrent.newUnmodifiableList(this.reactions),
                Concurrent.newUnmodifiableList(this.embeds),
                Concurrent.newUnmodifiableList(this.pages),
                Concurrent.newUnmodifiableList(this.items),
                this.content,
                this.option,
                this.itemStyle,
                this.fieldNames,
                this.itemsPerPage
            );
        }

    }

}
