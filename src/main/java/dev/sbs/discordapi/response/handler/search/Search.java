package dev.sbs.discordapi.response.handler.search;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.StringUtil;
import dev.sbs.api.util.builder.annotation.BuildFlag;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.response.component.interaction.action.TextInput;
import dev.sbs.discordapi.response.handler.sorter.Sorter;
import dev.sbs.discordapi.response.page.item.field.FieldItem;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiPredicate;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Search<T> {

    private final @NotNull TextInput textInput;
    private final @NotNull ConcurrentList<BiPredicate<T, String>> predicates;
    private @NotNull Optional<String> lastMatch = Optional.empty();

    public static <T> @NotNull Builder<T> builder() {
        return new Builder<>();
    }

    @SuppressWarnings("unchecked")
    private static <V> @Nullable V castSafely(@NotNull Class<V> type, @NotNull String value) {
        try {
            PropertyEditor propertyEditor = PropertyEditorManager.findEditor(type);
            propertyEditor.setAsText(value);
            return (V) propertyEditor.getValue();
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Search<?> search = (Search<?>) o;

        return new EqualsBuilder()
            .append(this.getTextInput(), search.getTextInput())
            .append(this.getPredicates(), search.getPredicates())
            .build();
    }

    public static <T> @NotNull Builder<T> from(@NotNull Search<T> searcher) {
        return new Builder<T>()
            .withLabel(searcher.getTextInput().getLabel().orElseThrow())
            .withPlaceholder(searcher.getTextInput().getPlaceholder())
            .withPredicates(searcher.getPredicates());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getTextInput())
            .append(this.getPredicates())
            .build();
    }

    public @NotNull Builder<T> mutate() {
        return from(this);
    }

    public void updateLastMatch(@NotNull TextInput textInput) {
        this.lastMatch = textInput.getValue();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<T> implements dev.sbs.api.util.builder.Builder<Search<T>> {

        @BuildFlag(nonNull = true)
        private TextInput.Builder textInputBuilder = TextInput.builder().withSearchType(TextInput.SearchType.CUSTOM);
        @BuildFlag(nonNull = true)
        private final ConcurrentList<BiPredicate<T, String>> predicates = Concurrent.newList();

        /**
         * Add custom search predicates for the {@link FieldItem FieldItems}.
         *
         * @param predicates A variable amount of predicates.
         */
        public Builder<T> withPredicates(@NotNull BiPredicate<T, String>... predicates) {
            return this.withPredicates(Arrays.asList(predicates));
        }

        /**
         * Add custom search predicates for the {@link FieldItem FieldItems}.
         *
         * @param predicates A variable amount of predicates.
         */
        public Builder<T> withPredicates(@NotNull Iterable<BiPredicate<T, String>> predicates) {
            predicates.forEach(this.predicates::add);
            return this;
        }

        /**
         * Add custom search predicates for the {@link FieldItem FieldItems}.
         *
         * @param predicates A variable amount of predicates.
         */
        public <V extends Serializable> Builder<T> withPredicates(@NotNull Class<V> type, @NotNull BiPredicate<T, V>... predicates) {
            return this.withPredicates(type, Arrays.asList(predicates));
        }

        /**
         * Add custom search predicates for the {@link FieldItem FieldItems}.
         *
         * @param predicates A variable amount of predicates.
         */
        public <V extends Serializable> Builder<T> withPredicates(@NotNull Class<V> type, @NotNull Iterable<BiPredicate<T, V>> predicates) {
            predicates.forEach(predicate -> this.predicates.add((t, value) -> predicate.test(t, Search.castSafely(type, value))));
            return this;
        }

        /**
         * Sets the placeholder of the {@link Sorter}.
         *
         * @param placeholder The placeholder to use.
         */
        public Builder<T> withPlaceholder(@Nullable String placeholder) {
            return this.withPlaceholder(Optional.ofNullable(placeholder));
        }

        /**
         * Sets the placeholder of the {@link Sorter}.
         *
         * @param placeholder The placeholder to use.
         * @param args The objects used to format the placeholder.
         */
        public Builder<T> withPlaceholder(@PrintFormat @Nullable String placeholder, @Nullable Object... args) {
            return this.withPlaceholder(StringUtil.formatNullable(placeholder, args));
        }

        /**
         * Sets the placeholder of the {@link Sorter}.
         *
         * @param placeholder The placeholder to use.
         */
        public Builder<T> withPlaceholder(@NotNull Optional<String> placeholder) {
            this.textInputBuilder.withPlaceholder(placeholder);
            return this;
        }

        /**
         * Sets the label of the {@link Sorter}.
         * <br><br>
         * This is used for the {@link Button}.
         *
         * @param label The label of the field item.
         */
        public Builder<T> withLabel(@NotNull String label) {
            this.textInputBuilder.withLabel(label);
            return this;
        }

        /**
         * Sets the label of the {@link Sorter}.
         * <br><br>
         * This is used for the {@link Button}.
         *
         * @param label The label of the field item.
         * @param args The objects used to format the label.
         */
        public Builder<T> withLabel(@PrintFormat @NotNull String label, @Nullable Object... args) {
            this.textInputBuilder.withLabel(label, args);
            return this;
        }

        @Override
        public @NotNull Search<T> build() {
            Reflection.validateFlags(this);

            return new Search<>(
                this.textInputBuilder.withValue(Optional.empty()).build(),
                this.predicates.toUnmodifiableList()
            );
        }

    }

}
