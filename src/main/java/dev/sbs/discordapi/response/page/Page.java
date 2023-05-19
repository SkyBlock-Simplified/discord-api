package dev.sbs.discordapi.response.page;

import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;
import dev.sbs.discordapi.response.component.type.PreservableComponent;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.response.page.handler.item.CustomItemHandler;
import dev.sbs.discordapi.response.page.handler.item.ItemHandler;
import dev.sbs.discordapi.response.page.item.Item;
import dev.sbs.discordapi.response.page.item.SingletonFieldItem;
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

public class Page extends Item implements SingletonFieldItem, Paging<Page> {

    @Getter private final @NotNull Optional<String> content;
    @Getter private final @NotNull ConcurrentList<Page> pages;
    @Getter private final @NotNull ConcurrentList<Embed> embeds;
    @Getter private final @NotNull ConcurrentList<LayoutComponent<ActionComponent>> components;
    @Getter private final @NotNull ConcurrentList<Emoji> reactions;
    @Getter private final @NotNull ItemHandler<?> itemHandler;

    protected Page(
        @NotNull String identifier,
        @NotNull Optional<SelectMenu.Option> option,
        @NotNull Optional<String> content,
        @NotNull ConcurrentList<Page> pages,
        @NotNull ConcurrentList<Embed> embeds,
        @NotNull ConcurrentList<LayoutComponent<ActionComponent>> components,
        @NotNull ConcurrentList<Emoji> reactions,
        @NotNull ItemHandler<?> itemHandler) {
        super(identifier, option, Type.PAGE, false);
        this.content = content;
        this.pages = pages.toUnmodifiableList();
        this.embeds = embeds.toUnmodifiableList();
        this.components = components.toUnmodifiableList();
        this.reactions = reactions.toUnmodifiableList();
        this.itemHandler = itemHandler;
    }

    public static PageBuilder builder() {
        return new PageBuilder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        Page page = (Page) o;

        return new EqualsBuilder()
            .append(this.getContent(), page.getContent())
            .append(this.getPages(), page.getPages())
            .append(this.getEmbeds(), page.getEmbeds())
            .append(this.getComponents(), page.getComponents())
            .append(this.getReactions(), page.getReactions())
            .append(this.getItemHandler(), page.getItemHandler())
            .build();
    }

    public final boolean doesHaveItems() {
        return ListUtil.notEmpty(this.getItemHandler().getItems());
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
            .withItemHandler(page.getItemHandler());
    }

    @Override
    public Field getRenderField() {
        return Field.builder()
            .withName(this.getOption().map(SelectMenu.Option::getLabel))
            .withValue("Goto page.")
            .isInline()
            .build();
    }

    public void gotoNextSorter() {
        this.getItemHandler().gotoNextSorter();
        this.getItemHandler().setCacheUpdateRequired();
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
            .build();
    }

    public void invertOrder() {
        this.getItemHandler().invertOrder();
        this.getItemHandler().setCacheUpdateRequired();
    }

    public PageBuilder mutate() {
        return from(this);
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
        private ItemHandler<?> itemHandler = CustomItemHandler.builder(Item.class).build();

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
         * @param itemHandler The item data for the page.
         */
        public PageBuilder withItemHandler(@NotNull ItemHandler<?> itemHandler) {
            this.itemHandler = itemHandler;
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
                this.itemHandler
            );
        }

    }

}
