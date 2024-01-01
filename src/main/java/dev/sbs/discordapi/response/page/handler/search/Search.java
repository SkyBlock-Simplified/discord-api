package dev.sbs.discordapi.response.page.handler.search;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.builder.annotation.BuildFlag;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.api.util.mutable.triple.Triple;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.response.component.interaction.action.TextInput;
import dev.sbs.discordapi.response.page.handler.sorter.Sorter;
import dev.sbs.discordapi.response.page.item.field.FieldItem;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Search<T, V extends Serializable> implements BiFunction<ConcurrentList<T>, String, Integer> {

    private final @NotNull TextInput textInput;
    private final @NotNull Class<V> type;
    private final @NotNull ConcurrentList<BiPredicate<T, V>> predicates;

    @Override
    public @NotNull Integer apply(@NotNull ConcurrentList<T> items, @NotNull String value) {
        return items.indexedStream()
            .filter((item, index, size) -> this.getPredicates()
                .stream()
                .anyMatch(predicate -> predicate.test(item, this.castSafely(value)))
            )
            .map(Triple::getMiddle)
            .findFirst()
            .orElse(-1L)
            .intValue();
    }

    public static <T, V extends Serializable> @NotNull Builder<T, V> builder() {
        return new Builder<>();
    }

    private @Nullable V castSafely(@NotNull String value) {
        try {
            return this.getType().cast(value);
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Search<?, ?> search = (Search<?, ?>) o;

        return new EqualsBuilder()
            .append(this.getTextInput(), search.getTextInput())
            .append(this.getPredicates(), search.getPredicates())
            .build();
    }

    public static <T, V extends Serializable> @NotNull Builder<T, V> from(@NotNull Search<T, V> searcher) {
        return new Builder<T, V>()
            .withTextInput(searcher.getTextInput())
            .withPredicates(searcher.getPredicates());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getTextInput())
            .append(this.getPredicates())
            .build();
    }

    public @NotNull Builder<T, V> mutate() {
        return from(this);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<T, V extends Serializable> implements dev.sbs.api.util.builder.Builder<Search<T, V>> {

        @BuildFlag(nonNull = true)
        private TextInput.Builder textInputBuilder = TextInput.builder();
        @BuildFlag(nonNull = true)
        private final ConcurrentList<BiPredicate<T, V>> predicates = Concurrent.newList();

        /**
         * Add custom search predicates for the {@link FieldItem FieldItems}.
         *
         * @param predicates A variable amount of predicates.
         */
        public Builder<T, V> withPredicates(@NotNull BiPredicate<T, V>... predicates) {
            return this.withPredicates(Arrays.asList(predicates));
        }

        /**
         * Add custom search predicates for the {@link FieldItem FieldItems}.
         *
         * @param predicates A variable amount of predicates.
         */
        public Builder<T, V> withPredicates(@NotNull Iterable<BiPredicate<T, V>> predicates) {
            predicates.forEach(this.predicates::add);
            return this;
        }

        /**
         * Sets the placeholder of the {@link Sorter}.
         *
         * @param placeholder The placeholder to use.
         */
        public Builder<T, V> withPlaceholder(@Nullable String placeholder) {
            return this.withPlaceholder(Optional.ofNullable(placeholder));
        }

        /**
         * Sets the placeholder of the {@link Sorter}.
         *
         * @param placeholder The placeholder to use.
         * @param args The objects used to format the placeholder.
         */
        public Builder<T, V> withPlaceholder(@PrintFormat @Nullable String placeholder, @Nullable Object... args) {
            return this.withPlaceholder(StringUtil.formatNullable(placeholder, args));
        }

        /**
         * Sets the placeholder of the {@link Sorter}.
         *
         * @param placeholder The placeholder to use.
         */
        public Builder<T, V> withPlaceholder(@NotNull Optional<String> placeholder) {
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
        public Builder<T, V> withLabel(@NotNull String label) {
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
        public Builder<T, V> withLabel(@PrintFormat @NotNull String label, @Nullable Object... args) {
            this.textInputBuilder.withLabel(label, args);
            return this;
        }

        public Builder<T, V> withTextInput(@NotNull TextInput textInput) {
            this.textInputBuilder = TextInput.from(textInput);
            return this;
        }

        @Override
        public @NotNull Search<T, V> build() {
            Reflection.validateFlags(this);

            return new Search<>(
                this.textInputBuilder.withValue(Optional.empty()).build(),
                Reflection.getSuperClass(this, 1),
                this.predicates.toUnmodifiableList()
            );
        }

    }

}
