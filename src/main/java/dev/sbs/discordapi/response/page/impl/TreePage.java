package dev.sbs.discordapi.response.page.impl;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.StringUtil;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.handler.Subpages;
import dev.sbs.discordapi.response.handler.history.TreeHistoryHandler;
import dev.sbs.discordapi.response.handler.item.ItemHandler;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.item.Item;
import dev.sbs.discordapi.response.page.item.field.PageItem;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class TreePage implements Page, Subpages<TreePage> {

    private final @NotNull SelectMenu.Option option;
    private final @NotNull ConcurrentList<LayoutComponent> components;
    private final @NotNull ConcurrentList<Emoji> reactions;
    private final @NotNull ItemHandler<?> itemHandler;
    private final @NotNull TreeHistoryHandler<PageItem, String> historyHandler;
    private final @NotNull ConcurrentList<TreePage> pages;

    // Legacy
    private final @NotNull Optional<String> content;
    private final @NotNull ConcurrentList<Embed> embeds;

    public static @NotNull TreePageBuilder builder() {
        return new TreePageBuilder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        TreePage page = (TreePage) o;

        return new EqualsBuilder()
            .append(this.getOption(), page.getOption())
            .append(this.getContent(), page.getContent())
            .append(this.getPages(), page.getPages())
            .append(this.getEmbeds(), page.getEmbeds())
            .append(this.getComponents(), page.getComponents())
            .append(this.getReactions(), page.getReactions())
            .append(this.getItemHandler(), page.getItemHandler())
            .append(this.getHistoryHandler(), page.getHistoryHandler())
            .build();
    }

    /**
     * Finds an existing {@link ActionComponent}.
     *
     * @param tClass   The component type to match.
     * @param function The method reference to match with.
     * @param value    The value to match with.
     * @return The matching component, if it exists.
     */
    public <S, T extends ActionComponent> Optional<T> findComponent(@NotNull Class<T> tClass, @NotNull Function<T, S> function, S value) {
        return this.getComponents()
            .stream()
            .map(layoutComponent -> layoutComponent.findComponent(tClass, function, value))
            .flatMap(Optional::stream)
            .findFirst();
    }

    public static @NotNull TreePageBuilder from(@NotNull TreePage page) {
        return new TreePageBuilder()
            .withOption(page.getOption())
            .withComponents(page.getComponents())
            .withReactions(page.getReactions())
            .withItemHandler(page.getItemHandler())
            .withPages(page.getPages())
            .withContent(page.getContent())
            .withEmbeds(page.getEmbeds());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(this.getContent())
            .append(this.getPages())
            .append(this.getEmbeds())
            .append(this.getComponents())
            .append(this.getReactions())
            .append(this.getItemHandler())
            .append(this.getHistoryHandler())
            .build();
    }

    @Override
    public @NotNull TreePageBuilder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    public static class TreePageBuilder extends Builder {

        private Optional<String> content = Optional.empty();
        private final ConcurrentList<TreePage> pages = Concurrent.newList();
        private final ConcurrentList<Embed> embeds = Concurrent.newList();
        private ItemHandler<?> itemHandler = ItemHandler.<Item>builder().build();

        /**
         * Clear all but preservable components from {@link TreePage}.
         */
        @Override
        public TreePageBuilder disableComponents() {
            return this.disableComponents(false);
        }

        /**
         * Clear all but preservable components from {@link TreePage}.
         *
         * @param recursive True to recursively clear components.
         */
        @Override
        public TreePageBuilder disableComponents(boolean recursive) {
            super.disableComponents(recursive);

            if (recursive)
                this.pages.forEach(page -> this.editPage(page.mutate().disableComponents(true).build()));

            // Remove Empty Layout Components
            this.components.removeIf(layoutComponent -> layoutComponent.getComponents().isEmpty());
            return this;
        }

        /**
         * Clear all pages from the {@link TreePage}.
         */
        public TreePageBuilder clearPages() {
            this.pages.clear();
            return this;
        }

        @Override
        public TreePageBuilder clearReaction(@NotNull Emoji emoji) {
            super.clearReaction(emoji);
            return this;
        }

        @Override
        public TreePageBuilder clearReactions() {
            super.clearReactions();
            return this;
        }

        /**
         * Edits an existing {@link TreePage} at the given index.
         *
         * @param pageBuilder The page builder to edit with.
         */
        public TreePageBuilder editPage(@NotNull Function<TreePageBuilder, TreePageBuilder> pageBuilder) {
            return this.editPage(0, pageBuilder);
        }

        /**
         * Edits an existing {@link TreePage} at the given index.
         *
         * @param index       The page index to edit.
         * @param pageBuilder The page builder to edit with.
         */
        public TreePageBuilder editPage(int index, @NotNull Function<TreePageBuilder, TreePageBuilder> pageBuilder) {
            if (index < this.pages.size())
                this.pages.set(index, pageBuilder.apply(this.pages.get(index).mutate()).build());

            return this;
        }

        /**
         * Updates an existing {@link TreePage}.
         *
         * @param page The page to edit.
         */
        public TreePageBuilder editPage(@NotNull TreePage page) {
            this.pages.stream()
                .filter(existingPage -> existingPage.getOption().getUniqueId().equals(page.getOption().getUniqueId()))
                .findFirst()
                .ifPresent(existingPage -> this.pages.set(this.pages.indexOf(existingPage), page));

            return this;
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the {@link TreePage}.
         *
         * @param components Variable number of layout components to add.
         */
        @Override
        public TreePageBuilder withComponents(@NotNull LayoutComponent... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the {@link TreePage}.
         *
         * @param components Collection of layout components to add.
         */
        @Override
        public TreePageBuilder withComponents(@NotNull Iterable<LayoutComponent> components) {
            super.withComponents(components);
            return this;
        }

        /**
         * Sets the content text to add to the {@link TreePage}.
         *
         * @param content The text to add to the page.
         */
        public TreePageBuilder withContent(@Nullable String content) {
            return this.withContent(Optional.ofNullable(content));
        }

        /**
         * Sets the content text to add to the {@link TreePage}.
         *
         * @param content The text to add to the page.
         * @param args The arguments to format the content with.
         */
        public TreePageBuilder withContent(@Nullable @PrintFormat String content, @Nullable Object... args) {
            return this.withContent(StringUtil.formatNullable(content, args));
        }

        /**
         * Sets the content text to add to the {@link TreePage}.
         *
         * @param content The text to add to the page.
         */
        public TreePageBuilder withContent(@NotNull Optional<String> content) {
            this.content = content;
            return this;
        }

        @Override
        public TreePageBuilder withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        @Override
        public TreePageBuilder withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        @Override
        public TreePageBuilder withDescription(@NotNull Optional<String> description) {
            super.withDescription(description);
            return this;
        }

        /**
         * Add {@link Embed Embeds} to the {@link TreePage}.
         *
         * @param embeds Variable number of embeds to add.
         */
        public TreePageBuilder withEmbeds(@NotNull Embed... embeds) {
            return this.withEmbeds(Arrays.asList(embeds));
        }

        /**
         * Add {@link Embed Embeds} to the {@link TreePage}.
         *
         * @param embeds Collection of embeds to add.
         */
        public TreePageBuilder withEmbeds(@NotNull Iterable<Embed> embeds) {
            embeds.forEach(this.embeds::add);
            return this;
        }

        @Override
        public TreePageBuilder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        @Override
        public TreePageBuilder withEmoji(@NotNull Optional<Emoji> emoji) {
            super.withEmoji(emoji);
            return this;
        }

        /**
         * Sets the item data to be used with the {@link TreePage}.
         *
         * @param itemHandler The item data for the page.
         */
        public TreePageBuilder withItemHandler(@NotNull ItemHandler<?> itemHandler) {
            this.itemHandler = itemHandler;
            return this;
        }

        @Override
        public TreePageBuilder withLabel(@NotNull String label) {
            super.withLabel(label);
            return this;
        }

        @Override
        public TreePageBuilder withLabel(@PrintFormat @NotNull String label, @Nullable Object... args) {
            super.withLabel(label, args);
            return this;
        }

        @Override
        public TreePageBuilder withOption(@NotNull SelectMenu.Option option) {
            super.withOption(option);
            return this;
        }

        /**
         * Add sub {@link TreePage Pages} to the {@link TreePage}.
         *
         * @param subPages Variable number of pages to add.
         */
        public TreePageBuilder withPages(@NotNull TreePage... subPages) {
            return this.withPages(Arrays.asList(subPages));
        }

        /**
         * Add sub {@link TreePage Pages} to the {@link TreePage}.
         *
         * @param subPages Collection of pages to add.
         */
        public TreePageBuilder withPages(@NotNull Iterable<TreePage> subPages) {
            subPages.forEach(this.pages::add);
            return this;
        }

        /**
         * Sets the reactions to add to the {@link TreePage}.
         *
         * @param reactions The reactions to add to the response.
         */
        @Override
        public TreePageBuilder withReactions(@NotNull Emoji... reactions) {
            return this.withReactions(Arrays.asList(reactions));
        }

        /**
         * Sets the reactions to add to the {@link TreePage}.
         *
         * @param reactions The reactions to add to the response.
         */
        @Override
        public TreePageBuilder withReactions(@NotNull Iterable<Emoji> reactions) {
            super.withReactions(reactions);
            return this;
        }

        @Override
        public TreePageBuilder withValue(@NotNull String value) {
            super.withLabel(value);
            return this;
        }

        @Override
        public TreePageBuilder withValue(@PrintFormat @NotNull String value, @Nullable Object... args) {
            super.withLabel(value, args);
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link TreePage}.
         */
        @Override
        public @NotNull TreePage build() {
            Reflection.validateFlags(this);

            // Prevent Empty Rows
            this.components.removeIf(layoutComponent -> layoutComponent.getComponents().isEmpty());

            return new TreePage(
                this.optionBuilder.build(),
                this.components.toUnmodifiableList(),
                this.reactions.toUnmodifiableList(),
                this.itemHandler,
                TreeHistoryHandler.<PageItem, String>builder()
                    .withPages(
                        this.itemHandler.getCachedFieldItems()
                            .stream()
                            .filter(PageItem.class::isInstance)
                            .map(PageItem.class::cast)
                            .collect(Concurrent.toList())
                    )
                    .withMatcher((page, identifier) -> page.getOption().getValue().equals(identifier))
                    .withTransformer(page -> page.getOption().getValue())
                    .build(),
                this.pages.toUnmodifiableList(),
                this.content,
                this.embeds.toUnmodifiableList()
            );
        }

    }

}
