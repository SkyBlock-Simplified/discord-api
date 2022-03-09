package dev.sbs.discordapi.response.page;

import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.api.util.helper.NumberUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.action.ActionComponent;
import dev.sbs.discordapi.response.component.action.Button;
import dev.sbs.discordapi.response.component.action.SelectMenu;
import dev.sbs.discordapi.response.component.layout.ActionRow;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;
import dev.sbs.discordapi.response.embed.Embed;
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

public class Page implements Paging {

    @Getter protected final UUID uniqueId;
    @Getter protected final ConcurrentList<LayoutComponent<?>> pageComponents;
    @Getter protected final ConcurrentList<Page> pages;
    @Getter protected final ConcurrentList<LayoutComponent<?>> components;
    @Getter protected final ConcurrentList<Emoji> reactions;
    @Getter protected final ConcurrentList<Embed> embeds;
    @Getter protected final ConcurrentList<PageItem> items;
    @Getter protected final Optional<String> content;
    @Getter protected final Optional<SelectMenu.Option> option;
    @Getter protected final boolean itemsInline;
    @Getter protected final int itemsPerPage;
    @Getter protected int itemPage = 1;

    protected Page(
        UUID uniqueId,
        ConcurrentList<LayoutComponent<?>> components,
        ConcurrentList<Emoji> reactions,
        ConcurrentList<Embed> embeds,
        ConcurrentList<Page> pages,
        ConcurrentList<PageItem> items,
        Optional<String> content,
        Optional<SelectMenu.Option> option,
        boolean inlineItems,
        int itemsPerPage) {
        this.pages = pages;
        this.uniqueId = uniqueId;
        this.components = components;
        this.reactions = reactions;
        this.embeds = embeds;
        this.items = items;
        this.option = option;
        this.content = content;
        this.itemsInline = inlineItems;
        this.itemsPerPage = itemsPerPage;

        // Page Components
        ConcurrentList<LayoutComponent<?>> pageComponents = Concurrent.newList();

        if (ListUtil.notEmpty(pages)) {
            // SubPage List
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
        return new PageBuilder(UUID.randomUUID());
    }

    /**
     * Updates an existing paging {@link Button}.
     *
     * @param buttonBuilder The button to edit.
     */
    private <S> void editPageButton(@NotNull Function<Button, S> function, S value, Function<Button.ButtonBuilder, Button.ButtonBuilder> buttonBuilder) {
        this.getPageComponents()
            .stream()
            .filter(ActionRow.class::isInstance)
            .map(ActionRow.class::cast)
            .forEach(layoutComponent -> layoutComponent.getComponents()
                .stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> Objects.equals(function.apply(button), value))
                .findFirst()
                .ifPresent(button -> {
                    int index = layoutComponent.getComponents().indexOf(button);
                    layoutComponent.getComponents().remove(index);
                    layoutComponent.getComponents().add(index, buttonBuilder.apply(button.mutate()).build());
                })
            );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Page page = (Page) o;

        return new EqualsBuilder()
            .append(this.isItemsInline(), page.isItemsInline())
            .append(this.getItemsPerPage(), page.getItemsPerPage())
            .append(this.getItemPage(), page.getItemPage())
            .append(this.getUniqueId(), page.getUniqueId())
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
    public <S, T extends ActionComponent<?, ?>> Optional<T> findComponent(@NotNull Class<T> tClass, @NotNull Function<T, S> function, S value) {
        return this.getComponents()
            .stream()
            .filter(ActionRow.class::isInstance)
            .map(ActionRow.class::cast)
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
     * @param uniqueId The unique id of the embed to search for.
     * @return The matching embed, if it exists.
     */
    public Optional<Embed> findEmbed(UUID uniqueId) {
        return this.getEmbeds()
            .stream()
            .filter(embed -> embed.getUniqueId().equals(uniqueId))
            .findFirst();
    }

    public static PageBuilder from(@NotNull Page page) {
        return new PageBuilder(page.getUniqueId())
            .withComponents(page.getComponents())
            .withReactions(page.getReactions())
            .withEmbeds(page.getEmbeds())
            .withPages(page.getPages())
            .withItems(page.getItems())
            .withContent(page.getContent())
            .withOption(page.getOption())
            .withInlineItems(page.isItemsInline())
            .withItemsPerPage(page.getItemsPerPage());
    }

    // Item Paging
    public final Optional<PageItem> getItem(int index) {
        return this.getItemPage() < this.getPages().size() ? Optional.of(this.getItems().get(index)) : Optional.empty();
    }

    public final int getTotalItemPages() {
        return (int) NumberUtil.round((double) this.items.size() / this.getItemsPerPage());
    }

    public final void gotoItemPage(int index) {
        this.itemPage = Math.min(this.items.size(), Math.max(1, index));
        this.updatePagingComponents();
    }

    public final void gotoFirstItemPage() {
        this.gotoItemPage(1);
    }

    public final void gotoLastItemPage() {
        this.gotoItemPage(this.items.size());
    }

    public final void gotoNextItemPage() {
        this.gotoItemPage(this.itemPage + 1);
    }

    public final void gotoPreviousItemPage() {
        this.gotoItemPage(this.itemPage - 1);
    }

    public final boolean hasNextItemPage() {
        return this.itemPage < this.getTotalItemPages();
    }

    public final boolean hasPreviousItemPage() {
        return this.itemPage > 1;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getUniqueId())
            .append(this.getPages())
            .append(this.getPageComponents())
            .append(this.getComponents())
            .append(this.getReactions())
            .append(this.getEmbeds())
            .append(this.getItems())
            .append(this.getContent())
            .append(this.getOption())
            .append(this.isItemsInline())
            .append(this.getItemsPerPage())
            .append(this.getItemPage())
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
        this.editPageButton(Button::getPageType, Button.PageType.INDEX, buttonBuilder -> buttonBuilder.withLabel(FormatUtil.format("{0} / {1}", this.getItemPage(), this.getTotalItemPages())));
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    public static class PageBuilder implements Builder<Page> {

        protected final UUID uniqueId;
        protected final ConcurrentList<Embed> embeds = Concurrent.newList();
        protected final ConcurrentList<LayoutComponent<?>> components = Concurrent.newList();
        protected final ConcurrentList<Emoji> reactions = Concurrent.newList();
        protected final ConcurrentList<Page> pages = Concurrent.newList();
        protected final ConcurrentList<PageItem> items = Concurrent.newList();
        protected Optional<String> content = Optional.empty();
        protected Optional<SelectMenu.Option> option = Optional.empty();
        protected boolean inlineItems;
        protected int itemsPerPage = 3;

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
        public PageBuilder editComponent(@NotNull ActionComponent<?, ?> actionComponent) {
            this.components.stream()
                .filter(ActionRow.class::isInstance)
                .map(ActionRow.class::cast)
                .forEach(layoutComponent -> layoutComponent.getComponents()
                    .stream()
                    .filter(innerComponent -> innerComponent.equals(actionComponent))
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
         * @param uniqueId The unique id of the embed to search for.
         * @param embedBuilder The embed builder to edit with.
         */
        public PageBuilder editEmbed(UUID uniqueId, Function<Embed.EmbedBuilder, Embed.EmbedBuilder> embedBuilder) {
            this.findEmbed(uniqueId).ifPresent(embed -> {
                Embed editedEmbed = embedBuilder.apply(embed.mutate()).build();

                // Locate and Update Existing Embed
                for (int i = 0; i < this.embeds.size(); i++) {
                    if (this.embeds.get(i).getUniqueId().equals(uniqueId)) {
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
        public PageBuilder editPage(@NotNull Function<Page.PageBuilder, Page.PageBuilder> pageBuilder) {
            return this.editPage(0, pageBuilder);
        }

        /**
         * Edits an existing {@link Page} at the given index.
         *
         * @param index The page index to edit.
         * @param pageBuilder The page builder to edit with.
         */
        public PageBuilder editPage(int index, @NotNull Function<Page.PageBuilder, Page.PageBuilder> pageBuilder) {
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
                .filter(existingPage -> existingPage.getUniqueId().equals(page.getUniqueId()))
                .findFirst()
                .ifPresent(existingPage -> this.pages.set(this.pages.indexOf(existingPage), page));

            return this;
        }

        /**
         * Finds an existing {@link Embed}.
         *
         * @param uniqueId The unique id of the embed to search for.
         * @return The matching embed, if it exists.
         */
        public Optional<Embed> findEmbed(UUID uniqueId) {
            return this.embeds.stream()
                .filter(embed -> embed.getUniqueId().equals(uniqueId))
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
        public <S, A extends ActionComponent<?, ?>> Optional<A> findComponent(@NotNull Class<A> tClass, @NotNull Function<A, S> function, S value) {
            return this.components
                .stream()
                .filter(ActionRow.class::isInstance)
                .map(ActionRow.class::cast)
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
        public PageBuilder withComponents(@NotNull LayoutComponent<?>... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the {@link Page}.
         *
         * @param components Collection of layout components to add.
         */
        public PageBuilder withComponents(@NotNull Iterable<LayoutComponent<?>> components) {
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
        public PageBuilder withEmbeds(Embed... embeds) {
            return this.withEmbeds(Arrays.asList(embeds));
        }

        /**
         * Add {@link Embed Embeds} to the {@link Page}.
         *
         * @param embeds Collection of embeds to add.
         */
        public PageBuilder withEmbeds(Iterable<Embed> embeds) {
            embeds.forEach(this.embeds::add);
            return this;
        }

        /**
         * Sets items to be rendered inline on the {@link Page}.
         */
        public PageBuilder withInlineItems() {
            return this.withInlineItems(true);
        }

        /**
         * Sets items to be rendered inline.
         *
         * @param inlineItems True to render items inline.
         */
        public PageBuilder withInlineItems(boolean inlineItems) {
            this.inlineItems = inlineItems;
            return this;
        }

        /**
         * Add a {@link PageItem} to the {@link Page}.
         *
         * @param pageItems Variable number of page items to add.
         */
        public PageBuilder withItems(@NotNull PageItem... pageItems) {
            return this.withItems(Arrays.asList(pageItems));
        }

        /**
         * Add a sub {@link PageItem} to the {@link Page}.
         *
         * @param pageItems Collection of page items to add.
         */
        public PageBuilder withItems(@NotNull Iterable<PageItem> pageItems) {
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
                this.uniqueId,
                Concurrent.newUnmodifiableList(this.components),
                Concurrent.newUnmodifiableList(this.reactions),
                Concurrent.newUnmodifiableList(this.embeds),
                Concurrent.newUnmodifiableList(this.pages),
                Concurrent.newUnmodifiableList(this.items),
                this.content,
                this.option,
                this.inlineItems,
                this.itemsPerPage
            );
        }

    }

}
