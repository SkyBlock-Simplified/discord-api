package dev.sbs.discordapi.response.handler.item.sorter;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.collection.concurrent.ConcurrentMap;
import dev.sbs.api.collection.sort.SortOrder;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.StringUtil;
import dev.sbs.api.util.builder.annotation.BuildFlag;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.page.item.Item;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings({ "unchecked", "rawtypes" })
public class Sorter<T> implements BiFunction<ConcurrentList<T>, Boolean, ConcurrentList<T>> {

    private final @NotNull SelectMenu.Option option;
    private final @NotNull ConcurrentMap<Comparator<? extends T>, SortOrder> comparators;
    private final @NotNull SortOrder order;

    @Override
    public @NotNull ConcurrentList<T> apply(@NotNull ConcurrentList<T> list, @NotNull Boolean reversed) {
        ConcurrentList<T> copy = Concurrent.newList(list).sorted((o1, o2) -> {
            Iterator<Map.Entry<Comparator<? extends T>, SortOrder>> iterator = this.getComparators().iterator();
            Map.Entry<Comparator<? extends T>, SortOrder> entry = iterator.next();
            Comparator comparator = entry.getKey();

            if (entry.getValue() == SortOrder.DESCENDING)
                comparator = comparator.reversed();

            while (iterator.hasNext()) {
                entry = iterator.next();
                comparator = comparator.thenComparing(entry.getKey());

                if (entry.getValue() == SortOrder.DESCENDING)
                    comparator = comparator.reversed();
            }

            return this.getOrder() == SortOrder.ASCENDING ? comparator.compare(o1, o2) : comparator.compare(o2, o1);
        });

        // Reverse Results
        if (reversed)
            copy = copy.inverse();

        return copy;
    }

    public static <T> @NotNull Builder<T> builder() {
        return new Builder<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Sorter<?> sorter = (Sorter<?>) o;

        return new EqualsBuilder()
            .append(this.getOption(), sorter.getOption())
            .append(this.getComparators(), sorter.getComparators())
            .append(this.getOrder(), sorter.getOrder())
            .build();
    }

    public static <T> @NotNull Builder<T> from(@NotNull Sorter<T> sorter) {
        return new Builder<T>()
            .withOption(sorter.getOption())
            .withComparators(sorter.getComparators())
            .withOrder(sorter.getOrder());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getOption())
            .append(this.getComparators())
            .append(this.getOrder())
            .build();
    }

    public @NotNull Builder<T> mutate() {
        return from(this);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<T> implements dev.sbs.api.util.builder.Builder<Sorter<T>> {

        @BuildFlag(nonNull = true)
        private SelectMenu.Option.Builder optionBuilder = SelectMenu.Option.builder();
        @BuildFlag(nonNull = true)
        private final ConcurrentMap<Comparator<? extends T>, SortOrder> comparators = Concurrent.newMap();
        @BuildFlag(nonNull = true)
        private SortOrder order = SortOrder.DESCENDING;

        /**
         * Add custom comparators for the {@link Item FieldItems}.
         *
         * @param comparators A variable amount of comparators.
         */
        public Builder<T> withComparators(@NotNull Comparator<? extends T>... comparators) {
            return this.withComparators(Arrays.asList(comparators));
        }

        /**
         * Add custom comparators for the {@link Item FieldItems}.
         *
         * @param order How the comparators are sorted.
         * @param comparators A variable amount of comparators.
         */
        public Builder<T> withComparators(@NotNull SortOrder order, @NotNull Comparator<? extends T>... comparators) {
            return this.withComparators(order, Arrays.asList(comparators));
        }

        /**
         * Add custom sort functions for the {@link Item FieldItems}.
         *
         * @param comparators A variable amount of comparators.
         */
        public Builder<T> withComparators(@NotNull Iterable<Comparator<? extends T>> comparators) {
            return this.withComparators(SortOrder.DESCENDING, comparators);
        }

        /**
         * Add custom sort functions for the {@link Item FieldItems}.
         *
         * @param order How the comparators are sorted.
         * @param comparators A variable amount of comparators.
         */
        public Builder<T> withComparators(@NotNull SortOrder order, @NotNull Iterable<Comparator<? extends T>> comparators) {
            comparators.forEach(comparator -> this.comparators.put(comparator, order));
            return this;
        }

        private Builder<T> withComparators(@NotNull ConcurrentMap<Comparator<? extends T>, SortOrder> comparators) {
            this.comparators.putAll(comparators);
            return this;
        }

        /**
         * Sets the description of the {@link Sorter}.
         *
         * @param description The description to use.
         */
        public Builder<T> withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        /**
         * Sets the description of the {@link Sorter}.
         *
         * @param description The description to use.
         * @param args The objects used to format the description.
         */
        public Builder<T> withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        /**
         * Sets the description of the {@link Sorter}.
         *
         * @param description The description to use.
         */
        public Builder<T> withDescription(@NotNull Optional<String> description) {
            this.optionBuilder.withDescription(description);
            return this;
        }

        /**
         * Sets the emoji of the {@link Sorter}.
         * <br><br>
         * This is used for the {@link Button#getEmoji()}.
         *
         * @param emoji The emoji to use.
         */
        public Builder<T> withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the emoji of the {@link Sorter}.
         * <br><br>
         * This is used for the {@link Button#getEmoji()}.
         *
         * @param emoji The emoji to use.
         */
        public Builder<T> withEmoji(@NotNull Optional<Emoji> emoji) {
            this.optionBuilder.withEmoji(emoji);
            return this;
        }

        /**
         * Add custom sort functions for the {@link Item FieldItems}.
         *
         * @param functions A variable amount of sort functions.
         */
        public Builder<T> withFunctions(@NotNull Function<T, ? extends Comparable>... functions) {
            return this.withFunctions(SortOrder.DESCENDING, functions);
        }

        /**
         * Add custom sort functions for the {@link Item FieldItems}.
         *
         * @param functions A variable amount of sort functions.
         * @param order How the comparators are sorted.
         */
        public Builder<T> withFunctions(@NotNull SortOrder order, @NotNull Function<T, ? extends Comparable>... functions) {
            return this.withFunctions(order, Arrays.asList(functions));
        }

        /**
         * Add custom sort functions for the {@link Item FieldItems}.
         *
         * @param functions A collection of sort functions.
         */
        public Builder<T> withFunctions(@NotNull Iterable<Function<T, ? extends Comparable>> functions) {
            return this.withFunctions(SortOrder.DESCENDING, functions);
        }

        /**
         * Add custom sort functions for the {@link Item FieldItems}.
         *
         * @param functions A collection of sort functions.
         * @param order How the comparators are sorted.
         */
        public Builder<T> withFunctions(@NotNull SortOrder order, @NotNull Iterable<Function<T, ? extends Comparable>> functions) {
            functions.forEach(function -> this.comparators.put(Comparator.comparing(function), order));
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
            this.optionBuilder.withLabel(label);
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
            this.optionBuilder.withLabel(label, args);
            return this;
        }

        public Builder<T> withOption(@NotNull SelectMenu.Option option) {
            this.optionBuilder = SelectMenu.Option.from(option);
            return this;
        }

        /**
         * Sets the sort order for the {@link Item PageItems}.
         * <br><br>
         * Descending - Highest to Lowest (Default)<br>
         * Ascending - Lowest to Highest
         *
         * @param order The order to sort the items in.
         */
        public Builder<T> withOrder(@NotNull SortOrder order) {
            this.order = order;
            return this;
        }

        @Override
        public @NotNull Sorter<T> build() {
            Reflection.validateFlags(this);

            return new Sorter<>(
                this.optionBuilder.build(),
                this.comparators.toUnmodifiableMap(),
                this.order
            );
        }

    }

}
