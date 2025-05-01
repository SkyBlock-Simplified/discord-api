package dev.sbs.discordapi.response.page;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.StringUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;
import dev.sbs.discordapi.response.component.type.ToggleableComponent;
import dev.sbs.discordapi.response.handler.ItemHandler;
import dev.sbs.discordapi.response.handler.history.HistoryHandler;
import dev.sbs.discordapi.response.page.impl.ContainerPage;
import dev.sbs.discordapi.response.page.impl.LegacyPage;
import dev.sbs.discordapi.response.page.impl.form.QuestionPage;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public interface Page {

    @NotNull ConcurrentList<LayoutComponent> getComponents();

    @NotNull HistoryHandler<?, String> getHistoryHandler();

    @NotNull ItemHandler<?> getItemHandler();

    @NotNull SelectMenu.Option getOption();

    @NotNull ConcurrentList<Emoji> getReactions();

    @NotNull PageBuilder mutate();

    // Builders

    static @NotNull ContainerPage.Builder builder() {
        return ContainerPage.builder();
    }

    static @NotNull QuestionPage.Builder form() {
        return QuestionPage.builder();
    }

    static @NotNull LegacyPage.Builder legacy() {
        return LegacyPage.builder();
    }

    static @NotNull ContainerPage.Builder from(@NotNull ContainerPage page) {
        return ContainerPage.from(page);
    }

    static @NotNull QuestionPage.Builder from(@NotNull QuestionPage questionPage) {
        return QuestionPage.from(questionPage);
    }

    static @NotNull LegacyPage.Builder from(@NotNull LegacyPage page) {
        return LegacyPage.from(page);
    }

    abstract class PageBuilder implements dev.sbs.api.util.builder.Builder<Page> {

        protected SelectMenu.Option.Builder optionBuilder = SelectMenu.Option.builder();
        protected final ConcurrentList<LayoutComponent> components = Concurrent.newList();
        protected final ConcurrentList<Emoji> reactions = Concurrent.newList();

        //private ItemHandler<?> itemHandler = ItemHandler.builder(Item.class).build();

        /**
         * Clear all but preservable components from {@link Page}.
         */
        public PageBuilder disableComponents() {
            return this.disableComponents(false);
        }

        /**
         * Clear all but preservable components from {@link Page}.
         *
         * @param recursive True to recursively clear components.
         */
        public PageBuilder disableComponents(boolean recursive) {
            this.components.forEach(this::toggleComponents);
            return this;
        }

        protected final @NotNull Component toggleComponents(@NotNull Component component) {
            if (component instanceof LayoutComponent layoutComponent) {
                layoutComponent.getComponents()
                    .stream()
                    .map(this::toggleComponents)
                    .filter(ToggleableComponent.class::isInstance)
                    .map(ToggleableComponent.class::cast)
                    .forEach(component1 -> layoutComponent.getComponents().set(
                        layoutComponent.getComponents().indexOf(component1),
                        component1.setState(false)
                    ));
            }

            return component;
        }

        public PageBuilder clearReaction(@NotNull Emoji emoji) {
            this.reactions.remove(emoji);
            return this;
        }

        public PageBuilder clearReactions() {
            this.reactions.clear();
            return this;
        }

        /**
         * Updates an existing {@link ActionComponent}.
         *
         * @param actionComponent The component to edit.
         */
        public PageBuilder editComponent(@NotNull ActionComponent actionComponent) {
            // TODO: Recursive component search
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
         * Finds an existing {@link ActionComponent}.
         *
         * @param tClass   The component type to match.
         * @param function The method reference to match with.
         * @param value    The value to match with.
         * @return The matching component, if it exists.
         */
        public final <S, A extends ActionComponent> Optional<A> findComponent(@NotNull Class<A> tClass, @NotNull Function<A, S> function, S value) {
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
        public PageBuilder withComponents(@NotNull LayoutComponent... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the {@link Page}.
         *
         * @param components Collection of layout components to add.
         */
        public PageBuilder withComponents(@NotNull Iterable<LayoutComponent> components) {
            components.forEach(this.components::add);
            return this;
        }

        public PageBuilder withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        public PageBuilder withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        public PageBuilder withDescription(@NotNull Optional<String> description) {
            this.optionBuilder.withDescription(description);
            return this;
        }

        public PageBuilder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        public PageBuilder withEmoji(@NotNull Optional<Emoji> emoji) {
            this.optionBuilder.withEmoji(emoji);
            return this;
        }

        public PageBuilder withLabel(@NotNull String label) {
            this.optionBuilder.withLabel(label);
            return this;
        }

        public PageBuilder withLabel(@PrintFormat @NotNull String label, @Nullable Object... args) {
            this.optionBuilder.withLabel(label, args);
            return this;
        }

        public PageBuilder withOption(@NotNull SelectMenu.Option option) {
            this.optionBuilder = SelectMenu.Option.from(option);
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

        public PageBuilder withValue(@NotNull String value) {
            this.optionBuilder.withValue(value);
            return this;
        }

        public PageBuilder withValue(@PrintFormat @NotNull String value, @Nullable Object... args) {
            this.optionBuilder.withValue(value, args);
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link Page}.
         */
        @Override
        public abstract @NotNull Page build();

    }

}
