package dev.sbs.discordapi.response.page;

import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.concurrent.Concurrent;
import dev.sbs.api.util.concurrent.ConcurrentList;
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

public class Page {

    @Getter protected final UUID uniqueId;
    @Getter protected final ConcurrentList<LayoutComponent<?>> pageComponents;
    @Getter protected final ConcurrentList<LayoutComponent<?>> components;
    @Getter protected final ConcurrentList<Emoji> reactions;
    @Getter protected final ConcurrentList<Embed> embeds;
    @Getter protected final ConcurrentList<Page> pages;
    @Getter protected final ConcurrentList<PageItem> items;
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
        Optional<SelectMenu.Option> option,
        boolean inlineItems,
        int itemsPerPage) {
        ConcurrentList<LayoutComponent<?>> pageComponents = Concurrent.newList();

        if (ListUtil.notEmpty(pages)) {
            // Page List
            pageComponents.add(ActionRow.of(
                SelectMenu.builder()
                    .isPageSelector(true)
                    .withPlaceholder("Select a page.")
                    .withOptions(
                        pages.stream()
                            .map(Page::getOption)
                            .flatMap(Optional::stream)
                            .collect(Concurrent.toList())
                    )
                    .build()
            ));
        } else if (ListUtil.notEmpty(items)) {
            // Item List
            pageComponents.add(ActionRow.of(
                Arrays.stream(Button.PageType.values())
                    .filter(Button.PageType::isForItemList)
                    .map(Button.PageType::build)
                    .collect(Concurrent.toList())
            ));

            // Item Selector
            pageComponents.add(ActionRow.of(
                SelectMenu.builder()
                    .isPageSelector(true)
                    .withPlaceholder("Select an item.")
                    .withOptions(
                        items.stream()
                            .map(PageItem::getOption)
                            .collect(Concurrent.toList())
                    )
                    .build()
            ));
        }

        this.pageComponents = Concurrent.newUnmodifiableList(pageComponents);
        this.uniqueId = uniqueId;
        this.components = components;
        this.reactions = reactions;
        this.embeds = embeds;
        this.pages = pages;
        this.items = items;
        this.option = option;
        this.itemsInline = inlineItems;
        this.itemsPerPage = itemsPerPage;
    }

    public static PageBuilder create() {
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
            .append(this.getItemPage(), page.getItemPage())
            .append(this.getUniqueId(), page.getUniqueId())
            .append(this.getComponents(), page.getComponents())
            .append(this.getReactions(), page.getReactions())
            .append(this.getEmbeds(), page.getEmbeds())
            .append(this.getPages(), page.getPages())
            .append(this.getItems(), page.getItems())
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

    public static PageBuilder from(Page page) {
        return new PageBuilder(page.getUniqueId())
            .withComponents(page.getComponents())
            .withReactions(page.getReactions())
            .withEmbeds(page.getEmbeds())
            .withPages(page.getPages())
            .withItems(page.getItems())
            .withOption(page.getOption());
    }

    public final Optional<PageItem> getItem(int index) {
        return this.getItemPage() < this.getPages().size() ? Optional.of(this.getItems().get(index)) : Optional.empty();
    }

    public final int getTotalItemPages() {
        return (int) NumberUtil.round((double) this.items.size() / this.getItemsPerPage());
    }

    public final void gotoItemPage(int index) {
        this.itemPage = Math.min(this.items.size(), Math.max(1, index));

        // Modify Page Components
        this.editPageButton(Button::getPageType, Button.PageType.FIRST, buttonBuilder -> buttonBuilder.setDisabled(this.hasPreviousItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.PREVIOUS, buttonBuilder -> buttonBuilder.setDisabled(this.hasPreviousItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.NEXT, buttonBuilder -> buttonBuilder.setDisabled(this.hasNextItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.LAST, buttonBuilder -> buttonBuilder.setDisabled(this.hasNextItemPage()));
        this.editPageButton(Button::getPageType, Button.PageType.INDEX, buttonBuilder -> buttonBuilder.withLabel(FormatUtil.format("{0} / {1}", this.getItemPage(), this.getTotalItemPages())));
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
        return this.itemPage < this.items.size();
    }

    public final boolean hasPreviousItemPage() {
        return this.itemPage > 1;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getUniqueId())
            .append(this.getComponents())
            .append(this.getReactions())
            .append(this.getEmbeds())
            .append(this.getPages())
            .append(this.getItems())
            .append(this.getOption())
            .append(this.isItemsInline())
            .append(this.getItemPage())
            .build();
    }

    public final boolean isItemSelector() {
        return ListUtil.notEmpty(this.items);
    }

    public PageBuilder modify() {
        return from(this);
    }

    public static class PageBuilder extends ContentBuilder<Page> {

        protected PageBuilder(UUID uniqueId) {
            super(uniqueId);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder clearComponents() {
            return this.clearComponents(true);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder clearComponents(boolean enforcePreserve) {
            super.clearComponents(enforcePreserve);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder clearPages() {
            super.clearPages();
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder editComponent(@NotNull ActionComponent<?, ?> actionComponent) {
            super.editComponent(actionComponent);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder editEmbed(UUID uniqueId, Function<Embed.EmbedBuilder, Embed.EmbedBuilder> embedBuilder) {
            super.editEmbed(uniqueId, embedBuilder);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder editPage(Function<Page.PageBuilder, Page.PageBuilder> pageBuilder) {
            return this.editPage(0, pageBuilder);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder editPage(int index, Function<Page.PageBuilder, Page.PageBuilder> pageBuilder) {
            super.editPage(index, pageBuilder);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder withComponents(@NotNull LayoutComponent<?>... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder withComponents(@NotNull Iterable<LayoutComponent<?>> components) {
            super.withComponents(components);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder withEmbeds(Embed... embeds) {
            return this.withEmbeds(Arrays.asList(embeds));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder withEmbeds(Iterable<Embed> embeds) {
            super.withEmbeds(embeds);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder withInlineItems() {
            return this.withInlineItems(true);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder withInlineItems(boolean inlineItems) {
            super.withInlineItems(inlineItems);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder withItems(@NotNull PageItem... pageItems) {
            return this.withItems(Arrays.asList(pageItems));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder withItems(@NotNull Iterable<PageItem> pageItems) {
            super.withItems(pageItems);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder withItemsPerPage(int itemsPerPage) {
            super.withItemsPerPage(itemsPerPage);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder withOption(@Nullable SelectMenu.Option option) {
            return this.withOption(Optional.ofNullable(option));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder withOption(@NotNull Optional<SelectMenu.Option> option) {
            super.withOption(option);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder withPages(@NotNull Page... pages) {
            return this.withPages(Arrays.asList(pages));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder withPages(@NotNull Iterable<Page> pages) {
            super.withPages(pages);
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder withReactions(@NotNull Emoji... reactions) {
            return this.withReactions(Arrays.asList(reactions));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PageBuilder withReactions(@NotNull Iterable<Emoji> reactions) {
            super.withReactions(reactions);
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
                this.option,
                this.inlineItems,
                this.itemsPerPage
            );
        }

    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    protected static abstract class ContentBuilder<T> implements Builder<T> {

        protected final UUID uniqueId;
        protected final ConcurrentList<Embed> embeds = Concurrent.newList();
        protected final ConcurrentList<LayoutComponent<?>> components = Concurrent.newList();
        protected final ConcurrentList<Emoji> reactions = Concurrent.newList();
        protected final ConcurrentList<Page> pages = Concurrent.newList();
        protected final ConcurrentList<PageItem> items = Concurrent.newList();
        protected Optional<SelectMenu.Option> option = Optional.empty();
        protected boolean inlineItems;
        protected int itemsPerPage = 3;

        /**
         * Clear all but preservable components from {@link Page}.
         */
        public ContentBuilder<?> clearComponents() {
            return this.clearComponents(true);
        }

        /**
         * Clear all components from {@link Page}.
         * <br><br>
         * True to leave preserved components.
         *
         * @param enforcePreserve True to leave preservable components.
         */
        public ContentBuilder<?> clearComponents(boolean enforcePreserve) {
            // Remove Possibly Preserved Components
            this.components.stream()
                .filter(layoutComponent -> !enforcePreserve || layoutComponent.notPreserved())
                .forEach(layoutComponent -> layoutComponent.getComponents()
                    .stream()
                    .filter(component -> !enforcePreserve || component.notPreserved())
                    .forEach(component -> layoutComponent.getComponents().remove(component))
                );

            // Remove Empty Layout Components
            this.components.removeIf(layoutComponent -> layoutComponent.getComponents().isEmpty());
            return this;
        }

        /**
         * Clear all pages from {@link Page}.
         */
        public ContentBuilder<?> clearPages() {
            this.pages.clear();
            return this;
        }

        /**
         * Updates an existing {@link ActionComponent}.
         *
         * @param actionComponent The component to edit.
         */
        public ContentBuilder<?> editComponent(@NotNull ActionComponent<?, ?> actionComponent) {
            this.components.stream()
                .filter(ActionRow.class::isInstance)
                .map(ActionRow.class::cast)
                .forEach(layoutComponent -> layoutComponent.getComponents()
                    .stream()
                    .filter(innerComponent -> innerComponent.equals(actionComponent))
                    .findFirst()
                    .ifPresent(innerComponent -> {
                        int index = layoutComponent.getComponents().indexOf(innerComponent);
                        layoutComponent.getComponents().remove(index);
                        layoutComponent.getComponents().add(index, actionComponent);
                    })
                );

            return this;
        }

        /**
         * Edits an existing {@link Embed}.
         *
         * @param uniqueId The unique id of the embed to search for.
         * @param embedBuilder The embed builder to edit with.
         */
        public ContentBuilder<?> editEmbed(UUID uniqueId, Function<Embed.EmbedBuilder, Embed.EmbedBuilder> embedBuilder) {
            this.findEmbed(uniqueId).ifPresent(embed -> {
                Embed editedEmbed = embedBuilder.apply(embed.mutate()).build();
                int index = -1;

                // Locate Existing Embed Index
                for (int i = 0; i < this.embeds.size(); i++) {
                    if (this.embeds.get(i).getUniqueId().equals(uniqueId)) {
                        index = i;
                        break;
                    }
                }

                // Update Embed
                if (index > -1) {
                    this.embeds.remove(index);
                    this.embeds.add(index, editedEmbed);
                }
            });

            return this;
        }

        /**
         * Edits an existing {@link Page} at the given index.
         *
         * @param pageBuilder The page builder to edit with.
         */
        public ContentBuilder<?> editPage(Function<Page.PageBuilder, Page.PageBuilder> pageBuilder) {
            return this.editPage(0, pageBuilder);
        }

        /**
         * Edits an existing {@link Page} at the given index.
         *
         * @param index The page index to edit.
         * @param pageBuilder The page builder to edit with.
         */
        public ContentBuilder<?> editPage(int index, Function<Page.PageBuilder, Page.PageBuilder> pageBuilder) {
            if (index < this.pages.size())
                this.pages.set(index, pageBuilder.apply(this.pages.get(index).modify()).build());

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
        public ContentBuilder<?> withComponents(@NotNull LayoutComponent<?>... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the {@link Page}.
         *
         * @param components Collection of layout components to add.
         */
        public ContentBuilder<?> withComponents(@NotNull Iterable<LayoutComponent<?>> components) {
            components.forEach(this.components::add);
            return this;
        }

        /**
         * Add {@link Embed Embeds} to the {@link Page}.
         *
         * @param embeds Variable number of embeds to add.
         */
        public ContentBuilder<?> withEmbeds(Embed... embeds) {
            return this.withEmbeds(Arrays.asList(embeds));
        }

        /**
         * Add {@link Embed Embeds} to the {@link Page}.
         *
         * @param embeds Collection of embeds to add.
         */
        public ContentBuilder<?> withEmbeds(Iterable<Embed> embeds) {
            embeds.forEach(this.embeds::add);
            return this;
        }

        /**
         * Sets items to be rendered inline on the {@link Page}.
         */
        public ContentBuilder<?> withInlineItems() {
            return this.withInlineItems(true);
        }

        /**
         * Sets items to be rendered inline.
         *
         * @param inlineItems True to render items inline.
         */
        public ContentBuilder<?> withInlineItems(boolean inlineItems) {
            this.inlineItems = inlineItems;
            return this;
        }

        /**
         * Add a {@link PageItem} to the {@link Page}.
         *
         * @param pageItems Variable number of page items to add.
         */
        public ContentBuilder<?> withItems(@NotNull PageItem... pageItems) {
            return this.withItems(Arrays.asList(pageItems));
        }

        /**
         * Add a sub {@link PageItem} to the {@link Page}.
         *
         * @param pageItems Collection of page items to add.
         */
        public ContentBuilder<?> withItems(@NotNull Iterable<PageItem> pageItems) {
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
        public ContentBuilder<?> withItemsPerPage(int itemsPerPage) {
            this.itemsPerPage = Math.max(1, itemsPerPage);
            return this;
        }

        /**
         * Define the {@link SelectMenu.Option} data of the {@link Page}.
         *
         * @param option The option to add.
         */
        public ContentBuilder<?> withOption(@Nullable SelectMenu.Option option) {
            return this.withOption(Optional.ofNullable(option));
        }

        /**
         * Define the {@link SelectMenu.Option} data of the {@link Page}.
         *
         * @param option The option to add.
         */
        public ContentBuilder<?> withOption(@NotNull Optional<SelectMenu.Option> option) {
            this.option = option;
            return this;
        }

        /**
         * Add a sub {@link Page} to the {@link Page}.
         *
         * @param pages Variable number of pages to add.
         */
        public ContentBuilder<?> withPages(@NotNull Page... pages) {
            return this.withPages(Arrays.asList(pages));
        }

        /**
         * Add a sub {@link Page} to the {@link Page}.
         *
         * @param pages Collection of pages to add.
         */
        public ContentBuilder<?> withPages(@NotNull Iterable<Page> pages) {
            pages.forEach(this.pages::add);
            return this;
        }

        /**
         * Sets the reactions to add to the {@link Page}.
         *
         * @param reactions The reactions to add to the response.
         */
        public ContentBuilder<?> withReactions(@NotNull Emoji... reactions) {
            return this.withReactions(Arrays.asList(reactions));
        }

        /**
         * Sets the reactions to add to the {@link Page}.
         *
         * @param reactions The reactions to add to the response.
         */
        public ContentBuilder<?> withReactions(@NotNull Iterable<Emoji> reactions) {
            reactions.forEach(this.reactions::add);
            return this;
        }

    }

}
