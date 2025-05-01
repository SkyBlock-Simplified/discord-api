package dev.sbs.discordapi.response.page.impl.form;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.StringUtil;
import dev.sbs.api.util.builder.annotation.BuildFlag;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;
import dev.sbs.discordapi.response.handler.history.IndexHistoryHandler;
import dev.sbs.discordapi.response.handler.item.ItemHandler;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.impl.LegacyPage;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionPage implements Page {

    // Page Details
    private final @NotNull SelectMenu.Option option;
    private final @NotNull ConcurrentList<LayoutComponent> components;
    private final @NotNull ConcurrentList<Emoji> reactions;
    private final @NotNull ItemHandler<Question<?>> itemHandler;
    private final @NotNull IndexHistoryHandler<Question<?>, String> historyHandler;
    
    // Form Details
    private final @NotNull String header;
    private final @NotNull Optional<String> details;

    public static @NotNull Builder builder() {
        return new Builder();
    }


    public static Builder from(@NotNull QuestionPage questionPage) {
        return new Builder()
            .withOption(questionPage.getOption())
            .withComponents(questionPage.getComponents())
            .withReactions(questionPage.getReactions())
            .withQuestions(questionPage.getItemHandler().getItems())
            .withTitle(questionPage.getHeader())
            .withDetails(questionPage.getDetails());
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder extends PageBuilder {

        @BuildFlag(notEmpty = true)
        private final ConcurrentList<Question<?>> questions = Concurrent.newList();
        @BuildFlag(notEmpty = true)
        private Optional<String> title = Optional.empty();
        private Optional<String> details = Optional.empty();

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

        public Builder withDetails(@Nullable String details) {
            return this.withDetails(Optional.ofNullable(details));
        }

        public Builder withDetails(@PrintFormat @Nullable String details, @Nullable Object... args) {
            return this.withDetails(StringUtil.formatNullable(details, args));
        }

        public Builder withDetails(@NotNull Optional<String> details) {
            this.details = details;
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

        public <T> Builder withQuestion(@NotNull Question<T> question) {
            return this.withQuestions(question);
        }

        public Builder withQuestions(@NotNull Question<?>... questions) {
            return this.withQuestions(Arrays.asList(questions));
        }

        public Builder withQuestions(@NotNull Iterable<Question<?>> questions) {
            questions.forEach(this.questions::add);
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

        public Builder withTitle(@Nullable String title) {
            return this.withTitle(Optional.ofNullable(title));
        }

        public Builder withTitle(@PrintFormat @Nullable String title, @Nullable Object... args) {
            return this.withTitle(StringUtil.formatNullable(title, args));
        }

        public Builder withTitle(@NotNull Optional<String> title) {
            this.title = title;
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
         * @return A built {@link QuestionPage}.
         */
        @Override
        public @NotNull QuestionPage build() {
            Reflection.validateFlags(this);

            // Prevent Empty Rows
            this.components.removeIf(layoutComponent -> layoutComponent.getComponents().isEmpty());

            return new QuestionPage(
                this.optionBuilder.build(),
                this.components.toUnmodifiableList(),
                this.reactions.toUnmodifiableList(),
                ItemHandler.<Question<?>>builder()
                    .withItems(this.questions.toUnmodifiableList())
                    .withTransformer((question, index, size) -> question.getFieldItem())
                    .build(),
                IndexHistoryHandler.<Question<?>, String>builder()
                    .withPages(this.questions.toUnmodifiableList())
                    .withMatcher((question, identifier) -> question.getIdentifier().equals(identifier))
                    .withTransformer(Question::getIdentifier)
                    .withMinimumSize(0)
                    .build(),
                this.title.orElseThrow(),
                this.details
            );

            // First Page
            /*if (this.defaultPage.isPresent() && response.getHistoryHandler().getPage(this.defaultPage.get()).isPresent())
                response.getHistoryHandler().locatePage(this.defaultPage.get());
            else {
                if (!this.pageHistory.isEmpty()) {
                    response.getHistoryHandler().locatePage(this.pageHistory.removeFirst());
                    this.pageHistory.forEach(identifier -> response.getHistoryHandler().gotoSubPage(identifier));
                    response.getHistoryHandler().getCurrentPage().getItemHandler().gotoItemPage(this.currentItemPage);
                } else
                    response.getHistoryHandler().gotoPage(response.getPages().get(0));
            }*/
        }

    }

}
