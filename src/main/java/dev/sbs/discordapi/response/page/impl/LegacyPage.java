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
import dev.sbs.discordapi.response.handler.history.TreeHistoryHandler;
import dev.sbs.discordapi.response.handler.item.ItemHandler;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.Subpages;
import dev.sbs.discordapi.response.page.item.Item;
import dev.sbs.discordapi.response.page.item.field.PageItem;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@Getter
@RequiredArgsConstructor
public class LegacyPage implements Page, Subpages<LegacyPage> {

    // Page Details
    private final @NotNull SelectMenu.Option option;
    private final @NotNull ConcurrentList<LayoutComponent> components;
    private final @NotNull ConcurrentList<Emoji> reactions;
    private final @NotNull ItemHandler<?> itemHandler;
    private final @NotNull TreeHistoryHandler<PageItem, String> historyHandler;

    // Legacy Details
    private final @NotNull ConcurrentList<LegacyPage> pages;
    private final @NotNull Optional<String> content;
    private final @NotNull ConcurrentList<Embed> embeds;

    public static @NotNull Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        LegacyPage page = (LegacyPage) o;

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

    public final boolean hasItems() {
        return this.getItemHandler().getItems().notEmpty();
    }

    public final boolean hasNoItems() {
        return !this.hasItems();
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
            .flatMap(layoutComponent -> layoutComponent.getComponents()
                .stream()
                .filter(tClass::isInstance)
                .map(tClass::cast)
                .filter(innerComponent -> Objects.equals(function.apply(innerComponent), value))
            )
            .findFirst();
    }

    public static @NotNull Builder from(@NotNull LegacyPage page) {
        return new Builder()
            .withOption(page.getOption())
            .withContent(page.getContent())
            .withPages(page.getPages())
            .withEmbeds(page.getEmbeds())
            .withComponents(page.getComponents())
            .withReactions(page.getReactions())
            .withItemHandler(page.getItemHandler());
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
    public @NotNull Builder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Builder extends PageBuilder {

        private Optional<String> content = Optional.empty();
        private final ConcurrentList<LegacyPage> pages = Concurrent.newList();
        private final ConcurrentList<Embed> embeds = Concurrent.newList();
        private ItemHandler<?> itemHandler = ItemHandler.<Item>builder().build();

        /**
         * Clear all but preservable components from {@link LegacyPage}.
         */
        @Override
        public Builder disableComponents() {
            return this.disableComponents(false);
        }

        /**
         * Clear all but preservable components from {@link LegacyPage}.
         *
         * @param recursive True to recursively clear components.
         */
        @Override
        public Builder disableComponents(boolean recursive) {
            super.disableComponents(recursive);

            if (recursive)
                this.pages.forEach(page -> this.editPage(page.mutate().disableComponents(true).build()));

            // Remove Empty Layout Components
            this.components.removeIf(layoutComponent -> layoutComponent.getComponents().isEmpty());
            return this;
        }

        /**
         * Clear all pages from the {@link LegacyPage}.
         */
        public Builder clearPages() {
            this.pages.clear();
            return this;
        }

        @Override
        public Builder clearReaction(@NotNull Emoji emoji) {
            super.clearReaction(emoji);
            return this;
        }

        @Override
        public Builder clearReactions() {
            super.clearReactions();
            return this;
        }

        /**
         * Edits an existing {@link LegacyPage} at the given index.
         *
         * @param pageBuilder The page builder to edit with.
         */
        public Builder editPage(@NotNull Function<Builder, Builder> pageBuilder) {
            return this.editPage(0, pageBuilder);
        }

        /**
         * Edits an existing {@link LegacyPage} at the given index.
         *
         * @param index       The page index to edit.
         * @param pageBuilder The page builder to edit with.
         */
        public Builder editPage(int index, @NotNull Function<Builder, Builder> pageBuilder) {
            if (index < this.pages.size())
                this.pages.set(index, pageBuilder.apply(this.pages.get(index).mutate()).build());

            return this;
        }

        /**
         * Updates an existing {@link LegacyPage}.
         *
         * @param page The page to edit.
         */
        public Builder editPage(@NotNull LegacyPage page) {
            this.pages.stream()
                .filter(existingPage -> existingPage.getOption().getUniqueId().equals(page.getOption().getUniqueId()))
                .findFirst()
                .ifPresent(existingPage -> this.pages.set(this.pages.indexOf(existingPage), page));

            return this;
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the {@link LegacyPage}.
         *
         * @param components Variable number of layout components to add.
         */
        @Override
        public Builder withComponents(@NotNull LayoutComponent... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the {@link LegacyPage}.
         *
         * @param components Collection of layout components to add.
         */
        @Override
        public Builder withComponents(@NotNull Iterable<LayoutComponent> components) {
            super.withComponents(components);
            return this;
        }

        /**
         * Sets the content text to add to the {@link LegacyPage}.
         *
         * @param content The text to add to the page.
         */
        public Builder withContent(@Nullable String content) {
            return this.withContent(Optional.ofNullable(content));
        }

        /**
         * Sets the content text to add to the {@link LegacyPage}.
         *
         * @param content The text to add to the page.
         * @param args The arguments to format the content with.
         */
        public Builder withContent(@Nullable @PrintFormat String content, @Nullable Object... args) {
            return this.withContent(StringUtil.formatNullable(content, args));
        }

        /**
         * Sets the content text to add to the {@link LegacyPage}.
         *
         * @param content The text to add to the page.
         */
        public Builder withContent(@NotNull Optional<String> content) {
            this.content = content;
            return this;
        }

        @Override
        public Builder withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        @Override
        public Builder withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        @Override
        public Builder withDescription(@NotNull Optional<String> description) {
            super.withDescription(description);
            return this;
        }

        /**
         * Add {@link Embed Embeds} to the {@link LegacyPage}.
         *
         * @param embeds Variable number of embeds to add.
         */
        public Builder withEmbeds(@NotNull Embed... embeds) {
            return this.withEmbeds(Arrays.asList(embeds));
        }

        /**
         * Add {@link Embed Embeds} to the {@link LegacyPage}.
         *
         * @param embeds Collection of embeds to add.
         */
        public Builder withEmbeds(@NotNull Iterable<Embed> embeds) {
            embeds.forEach(this.embeds::add);
            return this;
        }

        @Override
        public Builder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        @Override
        public Builder withEmoji(@NotNull Optional<Emoji> emoji) {
            super.withEmoji(emoji);
            return this;
        }

        /**
         * Sets the item data to be used with the {@link LegacyPage}.
         *
         * @param itemHandler The item data for the page.
         */
        public Builder withItemHandler(@NotNull ItemHandler<?> itemHandler) {
            this.itemHandler = itemHandler;
            return this;
        }

        @Override
        public Builder withLabel(@NotNull String label) {
            super.withLabel(label);
            return this;
        }

        @Override
        public Builder withLabel(@PrintFormat @NotNull String label, @Nullable Object... args) {
            super.withLabel(label, args);
            return this;
        }

        @Override
        public Builder withOption(@NotNull SelectMenu.Option option) {
            super.withOption(option);
            return this;
        }

        /**
         * Add sub {@link LegacyPage Pages} to the {@link LegacyPage}.
         *
         * @param subPages Variable number of pages to add.
         */
        public Builder withPages(@NotNull LegacyPage... subPages) {
            return this.withPages(Arrays.asList(subPages));
        }

        /**
         * Add sub {@link LegacyPage Pages} to the {@link LegacyPage}.
         *
         * @param subPages Collection of pages to add.
         */
        public Builder withPages(@NotNull Iterable<LegacyPage> subPages) {
            subPages.forEach(this.pages::add);
            return this;
        }

        /**
         * Sets the reactions to add to the {@link LegacyPage}.
         *
         * @param reactions The reactions to add to the response.
         */
        @Override
        public Builder withReactions(@NotNull Emoji... reactions) {
            return this.withReactions(Arrays.asList(reactions));
        }

        /**
         * Sets the reactions to add to the {@link LegacyPage}.
         *
         * @param reactions The reactions to add to the response.
         */
        @Override
        public Builder withReactions(@NotNull Iterable<Emoji> reactions) {
            super.withReactions(reactions);
            return this;
        }

        @Override
        public Builder withValue(@NotNull String value) {
            super.withLabel(value);
            return this;
        }

        @Override
        public Builder withValue(@PrintFormat @NotNull String value, @Nullable Object... args) {
            super.withLabel(value, args);
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link LegacyPage}.
         */
        @Override
        public @NotNull LegacyPage build() {
            Reflection.validateFlags(this);

            // Prevent Empty Rows
            this.components.removeIf(layoutComponent -> layoutComponent.getComponents().isEmpty());

            return new LegacyPage(
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
