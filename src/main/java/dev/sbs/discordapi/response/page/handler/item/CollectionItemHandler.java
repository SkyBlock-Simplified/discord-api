package dev.sbs.discordapi.response.page.handler.item;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.builder.annotation.BuildFlag;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.data.tuple.triple.Triple;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.api.util.helper.NumberUtil;
import dev.sbs.api.util.stream.triple.TriFunction;
import dev.sbs.api.util.stream.triple.TriPredicate;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.item.type.Item;
import dev.sbs.discordapi.response.page.item.type.RenderItem;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

@Getter
public class CollectionItemHandler<T> extends ItemHandler<T> {

    private final @NotNull TriPredicate<T, Long, Long> NOOP_FILTER = (__, index, size) -> true;
    private final @NotNull ConcurrentList<TriPredicate<T, Long, Long>> filters;
    private final @NotNull TriFunction<T, Long, Long, RenderItem> transformer;

    private CollectionItemHandler(
        @NotNull Class<T> type,
        @NotNull ConcurrentList<T> items,
        @NotNull ConcurrentList<Sorter<T>> sorters,
        @NotNull Item.Style style,
        @NotNull Optional<Triple<String, String, String>> columnNames,
        int amountPerPage,
        boolean viewerEnabled,
        @NotNull ConcurrentList<TriPredicate<T, Long, Long>> filters,
        @NotNull TriFunction<T, Long, Long, RenderItem> transformer) {
        super(type, items, sorters, style, columnNames, amountPerPage, viewerEnabled);
        this.filters = filters;
        this.transformer = transformer;
    }

    public static <T> Builder<T> builder(@NotNull Class<T> type) {
        return new Builder<>(type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        CollectionItemHandler<?> that = (CollectionItemHandler<?>) o;

        return new EqualsBuilder()
            .append(this.getTransformer(), that.getTransformer())
            .build();
    }

    public static <T> CollectionItemHandler.Builder<T> from(CollectionItemHandler<T> itemHandler) {
        return builder(itemHandler.getType())
            .withItems(itemHandler.getItems())
            .withSorters(itemHandler.getSorters())
            .withStyle(itemHandler.getStyle())
            .withColumnNames(itemHandler.getColumnNames())
            .withAmountPerPage(itemHandler.getAmountPerPage())
            .isViewerEnabled(itemHandler.isViewerEnabled())
            .withTransformer(itemHandler.getTransformer());
    }

    @Override
    public final @NotNull ConcurrentList<Item> getFieldItems(int startIndex, int endIndex) {
        return this.getCurrentSorter()
            .map(sorter -> sorter.apply(this.getItems(), this.isReversed()))
            .orElse(this.getItems())
            .subList(startIndex, endIndex)
            .indexedStream()
            .filter((t, index, size) -> this.getFilters()
                .stream()
                .allMatch(predicate -> predicate.test(t, index, size))
            )
            .map(this.getTransformer())
            .filter(Item.class::isInstance)
            .map(Item.class::cast)
            .collect(Concurrent.toList());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(this.getTransformer())
            .build();
    }

    public CollectionItemHandler.Builder<T> mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<T> implements dev.sbs.api.util.builder.Builder<CollectionItemHandler<T>> {

        private final Class<T> type;
        private final ConcurrentList<T> items = Concurrent.newList();
        private final ConcurrentList<TriPredicate<T, Long, Long>> filters = Concurrent.newList();
        private final ConcurrentList<Sorter<T>> sorters = Concurrent.newList();
        @BuildFlag(required = true)
        private Optional<TriFunction<T, Long, Long, RenderItem>> transformer = Optional.empty();
        @BuildFlag(required = true)
        private Item.Style style = Item.Style.FIELD_INLINE;
        private Optional<Triple<String, String, String>> columnNames = Optional.empty();
        private int amountPerPage = 12;
        private boolean viewerEnabled = false;

        /**
         * Clear all items from the {@link ItemHandler} list.
         */
        public Builder<T> clearItems() {
            this.items.clear();
            return this;
        }

        /**
         * Sets the item viewer as enabled.
         */
        public Builder<T> isViewerEnabled() {
            return this.isViewerEnabled(true);
        }

        /**
         * Sets the item viewer state.
         *
         * @param value True to enable the item selector.
         */
        public Builder<T> isViewerEnabled(boolean value) {
            this.viewerEnabled = value;
            return this;
        }

        /**
         * Sets the amount of {@link Item PageItems} to render per {@link Page}.
         * <br><br>
         * Defaults to 12.<br>
         * Minimum required is 1.<br>
         * Maximum allowed is 24.
         *
         * @param amountPerPage Number of items to render.
         */
        public Builder<T> withAmountPerPage(int amountPerPage) {
            this.amountPerPage = NumberUtil.ensureRange(amountPerPage, 1, 24);
            return this;
        }

        /**
         * Sets the field names to use when rendering {@link Item PageItems}.
         *
         * @param columnOne The field name for page items in column 1.
         * @param columnTwo The field name for page items in column 2.
         * @param columnThree The field name for page items in column 3.
         */
        public Builder<T> withColumnNames(@Nullable String columnOne, @Nullable String columnTwo, @Nullable String columnThree) {
            return this.withColumnNames(Triple.of(columnOne, columnTwo, columnThree));
        }

        /**
         * Sets the field names to use when rendering {@link Item PageItems}.
         *
         * @param fieldNames The field names for page items.
         */
        public Builder<T> withColumnNames(@Nullable Triple<String, String, String> fieldNames) {
            return this.withColumnNames(Optional.ofNullable(fieldNames));
        }

        /**
         * Sets the field names to use when rendering {@link Item PageItems}.
         *
         * @param fieldNames The field names for page items.
         */
        public Builder<T> withColumnNames(@NotNull Optional<Triple<String, String, String>> fieldNames) {
            this.columnNames = fieldNames;
            return this;
        }

        /**
         * Adds filters used to render {@link RenderItem RenderItems}.
         *
         * @param filters Collection of filters to apply to {@link T}.
         */
        public Builder<T> withFilters(@NotNull TriPredicate<T, Long, Long>... filters) {
            return this.withFilters(Arrays.asList(filters));
        }

        /**
         * Adds filters used to render {@link RenderItem RenderItems}.
         *
         * @param filters Collection of filters to apply to {@link T}.
         */
        public Builder<T> withFilters(@NotNull Iterable<TriPredicate<T, Long, Long>> filters) {
            filters.forEach(this.filters::add);
            return this;
        }

        /**
         * Add {@link RenderItem FieldItems} to the {@link Page} item list.
         *
         * @param fieldItems Variable number of field items to add.
         */
        public Builder<T> withItems(@NotNull T... fieldItems) {
            return this.withItems(Arrays.asList(fieldItems));
        }

        /**
         * Add {@link RenderItem FieldItems} to the {@link Page} item list.
         *
         * @param fieldItems Collection of field items to add.
         */
        public Builder<T> withItems(@NotNull Iterable<T> fieldItems) {
            fieldItems.forEach(this.items::add);
            return this;
        }

        /**
         * Add custom sort filter for the {@link Item FieldItems}.
         *
         * @param sorters A variable amount of filters.
         */
        public Builder<T> withSorters(@NotNull Sorter<T>... sorters) {
            return this.withSorters(Arrays.asList(sorters));
        }

        /**
         * Add custom sort filters for the {@link Item FieldItems}.
         *
         * @param sorters A variable amount of filters.
         */
        public Builder<T> withSorters(@NotNull Iterable<Sorter<T>> sorters) {
            sorters.forEach(this.sorters::add);
            return this;
        }

        /**
         * Sets the transformer used to render {@link RenderItem RenderItems}.
         *
         * @param transformer How to render {@link T}.
         */
        public Builder<T> withTransformer(@Nullable TriFunction<T, Long, Long, RenderItem> transformer) {
            return this.withTransformer(Optional.ofNullable(transformer));
        }

        /**
         * Sets the transformer used to render {@link RenderItem RenderItems}.
         *
         * @param transformer How to render {@link T}.
         */
        public Builder<T> withTransformer(@Nullable Optional<TriFunction<T, Long, Long, RenderItem>> transformer) {
            this.transformer = transformer;
            return this;
        }

        /**
         * Sets the render style for {@link Item PageItems}.
         *
         * @param itemStyle The page item style.
         */
        public Builder<T> withStyle(@NotNull Item.Style itemStyle) {
            this.style = itemStyle;
            return this;
        }

        @Override
        public @NotNull CollectionItemHandler<T> build() {
            Reflection.validateFlags(this);

            CollectionItemHandler<T> itemHandler = new CollectionItemHandler<>(
                this.type,
                this.items.toUnmodifiableList(),
                this.sorters,
                this.style,
                this.columnNames,
                this.amountPerPage,
                this.viewerEnabled,
                this.filters,
                this.transformer.orElseThrow()
            );

            if (ListUtil.notEmpty(this.sorters))
                itemHandler.gotoNextSorter();

            itemHandler.setCacheUpdateRequired();
            return itemHandler;
        }

    }

}
