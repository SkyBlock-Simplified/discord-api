package dev.sbs.discordapi.response.page.handler.item;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.collection.sort.SortOrder;
import dev.sbs.api.util.data.tuple.Triple;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.api.util.helper.NumberUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.page.handler.CacheHandler;
import dev.sbs.discordapi.response.page.item.Item;
import dev.sbs.discordapi.util.exception.DiscordException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ItemHandler<T> implements CacheHandler {

    @Getter private final @NotNull Class<T> type;
    @Getter private final @NotNull ConcurrentList<T> items;
    @Getter private final @NotNull ConcurrentList<Sorter<T>> sorters;
    @Getter private final @NotNull Item.Style style;
    @Getter private final @NotNull Optional<Triple<String, String, String>> columnNames;
    @Getter private final int amountPerPage;
    @Getter private final boolean viewerEnabled;
    @Getter private int currentSorterIndex = -1;
    @Getter private boolean reversed = false;
    @Getter private int currentItemPage = 1;
    @Getter @Setter private boolean cacheUpdateRequired;
    private ConcurrentList<Item> cachedItems = Concurrent.newUnmodifiableList();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ItemHandler<?> that = (ItemHandler<?>) o;

        return new EqualsBuilder()
            .append(this.getAmountPerPage(), that.getAmountPerPage())
            .append(this.isViewerEnabled(), that.isViewerEnabled())
            .append(this.getCurrentSorterIndex(), that.getCurrentSorterIndex())
            .append(this.isReversed(), that.isReversed())
            .append(this.getCurrentItemPage(), that.getCurrentItemPage())
            .append(this.getType(), that.getType())
            .append(this.getItems(), that.getItems())
            .append(this.getSorters(), that.getSorters())
            .append(this.getStyle(), that.getStyle())
            .append(this.getColumnNames(), that.getColumnNames())
            .append(this.getCachedItems(), that.getCachedItems())
            .build();
    }

    public @NotNull ConcurrentList<Item> getCachedItems() {
        if (this.isCacheUpdateRequired()) {
            int startIndex = (this.getCurrentItemPage() - 1) * this.getAmountPerPage();
            int endIndex = Math.min(startIndex + this.getAmountPerPage(), ListUtil.sizeOf(this.getItems()));
            this.cachedItems = this.getFieldItems(startIndex, endIndex).toUnmodifiableList();
        }

        return this.cachedItems;
    }

    public Optional<Sorter<T>> getCurrentSorter() {
        return Optional.ofNullable(this.getCurrentSorterIndex() > -1 ? this.getSorters().get(this.getCurrentSorterIndex()) : null);
    }

    public final @NotNull ConcurrentList<Item> getFieldItems() {
        return this.getFieldItems(0, ListUtil.sizeOf(this.getItems()));
    }

    public abstract @NotNull ConcurrentList<Item> getFieldItems(int startIndex, int endIndex);

    public final int getTotalItemPages() {
        return NumberUtil.roundUp((double) this.getItems().size() / this.getAmountPerPage(), 1);
    }

    public final void gotoItemPage(int index) {
        this.currentItemPage = NumberUtil.ensureRange(index, 1, this.getItems().size());
        this.setCacheUpdateRequired();
    }

    public final void gotoFirstItemPage() {
        this.gotoItemPage(1);
    }

    public final void gotoLastItemPage() {
        this.gotoItemPage(this.getTotalItemPages());
    }

    public final void gotoNextItemPage() {
        this.gotoItemPage(this.currentItemPage + 1);
    }

    public void gotoNextSorter() {
        if (ListUtil.notEmpty(this.getSorters())) {
            this.currentSorterIndex++;

            if (this.currentSorterIndex >= ListUtil.sizeOf(this.getSorters()))
                this.currentSorterIndex = 0;

            this.setCacheUpdateRequired();
        }
    }

    public final void gotoPreviousItemPage() {
        this.gotoItemPage(this.currentItemPage - 1);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getType())
            .append(this.getItems())
            .append(this.getSorters())
            .append(this.getStyle())
            .append(this.getColumnNames())
            .append(this.getAmountPerPage())
            .append(this.isViewerEnabled())
            .append(this.getCurrentSorterIndex())
            .append(this.isReversed())
            .append(this.getCurrentItemPage())
            .append(this.getCachedItems())
            .build();
    }

    public final boolean hasNextItemPage() {
        return this.currentItemPage < this.getTotalItemPages();
    }

    public final boolean hasPreviousItemPage() {
        return this.currentItemPage > 1;
    }

    public void invertOrder() {
        this.reversed = !this.isReversed();
        this.setCacheUpdateRequired();
    }

    public void setReversed() {
        this.setReversed(true);
    }

    public void setReversed(boolean value) {
        this.reversed = value;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static class Sorter<T> implements BiFunction<ConcurrentList<T>, Boolean, ConcurrentList<T>> {

        @Getter private final @NotNull SelectMenu.Option option;
        @Getter private final @NotNull ConcurrentMap<Comparator<? extends T>, SortOrder> comparators;
        @Getter private final @NotNull SortOrder order;

        @Override
        public ConcurrentList<T> apply(ConcurrentList<T> list, Boolean reversed) {
            ConcurrentList<T> copy = Concurrent.newList(list);

            copy.sort((o1, o2) -> {
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

        public static <T> Builder<T> builder() {
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

        public static <T> Builder<T> from(@NotNull Sorter<T> sorter) {
            return new Builder<>()
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

        public Builder<T> mutate() {
            return from(this);
        }

        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class Builder<T> implements dev.sbs.api.util.builder.Builder<Sorter<T>> {

            private SelectMenu.Option.Builder optionBuilder = SelectMenu.Option.builder();
            private final ConcurrentMap<Comparator<? extends T>, SortOrder> comparators = Concurrent.newMap();
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

            /**
             * Sets the description of the {@link Sorter}.
             *
             * @param description The description to use.
             * @param objects The objects used to format the description.
             */
            public Builder<T> withDescription(@Nullable String description, @NotNull Object... objects) {
                return this.withDescription(FormatUtil.formatNullable(description, objects));
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
             * @param objects The objects used to format the label.
             */
            public Builder withLabel(@NotNull String label, @NotNull Object... objects) {
                this.optionBuilder.withLabel(label, objects);
                return this;
            }

            public Builder withOption(@NotNull SelectMenu.Option option) {
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
            public Sorter<T> build() {
                if (ListUtil.isEmpty(this.comparators))
                    throw SimplifiedException.of(DiscordException.class)
                        .withMessage("Comparators cannot be empty!")
                        .build();

                return new Sorter<>(
                    this.optionBuilder.build(),
                    Concurrent.newUnmodifiableMap(this.comparators),
                    this.order
                );
            }

        }

    }

}
