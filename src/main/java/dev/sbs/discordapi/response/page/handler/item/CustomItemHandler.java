package dev.sbs.discordapi.response.page.handler.item;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.api.util.helper.NumberUtil;
import dev.sbs.api.util.mutable.tuple.pair.Pair;
import dev.sbs.api.util.mutable.tuple.triple.Triple;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.item.type.Item;
import dev.sbs.discordapi.response.page.item.type.RenderItem;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public class CustomItemHandler<T extends Item> extends ItemHandler<T> {

    private CustomItemHandler(
        @NotNull Class<T> type,
        @NotNull ConcurrentList<T> items,
        @NotNull ConcurrentList<Sorter<T>> sorters,
        @NotNull Item.Style style,
        @NotNull Optional<Triple<String, String, String>> columnNames,
        @NotNull ConcurrentMap<String, Object> variables,
        @NotNull ConcurrentList<Item> customItems,
        int amountPerPage,
        boolean viewerEnabled
    ) {
        super(type, items, sorters, style, columnNames, variables, customItems, amountPerPage, viewerEnabled);
    }

    public static <T extends Item> @NotNull Builder<T> builder(@NotNull Class<T> type) {
        return new Builder<>(type);
    }

    public static <T extends Item> @NotNull Builder<T> from(@NotNull CustomItemHandler<T> itemHandler) {
        return builder(itemHandler.getType())
            .withItems(itemHandler.getItems())
            .withSorters(itemHandler.getSorters())
            .withStyle(itemHandler.getStyle())
            .withColumnNames(itemHandler.getColumnNames())
            .withVariables(itemHandler.getVariables())
            .withCustomItems(itemHandler.getCustomItems())
            .withAmountPerPage(itemHandler.getAmountPerPage())
            .isViewerEnabled(itemHandler.isViewerEnabled());
    }

    @Override
    public final @NotNull ConcurrentList<Item> getFieldItems(int startIndex, int endIndex) {
        this.variables.put("FILTERED_SIZE", this.getItems().size());

        return this.getCurrentSorter()
            .map(sorter -> sorter.apply(this.getItems(), this.isReversed()))
            .orElse(this.getItems())
            .subList(startIndex, endIndex)
            .stream()
            .collect(Concurrent.toList());
    }

    public @NotNull Builder<T> mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<T extends Item> implements dev.sbs.api.util.builder.Builder<CustomItemHandler<T>> {

        private final Class<T> type;
        private final ConcurrentList<T> items = Concurrent.newList();
        private final ConcurrentList<Sorter<T>> sorters = Concurrent.newList();
        private Item.Style style = Item.Style.FIELD_INLINE;
        private Optional<Triple<String, String, String>> columnNames = Optional.empty();
        private int amountPerPage = 12;
        private boolean viewerEnabled = false;
        private ConcurrentMap<String, Object> variables = Concurrent.newMap();
        private ConcurrentList<Item> customItems = Concurrent.newList();

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
         * Add {@link Item Items} to the {@link Page} item list.
         *
         * @param customItems Variable number of non-field items to add.
         */
        public Builder<T> withCustomItems(@NotNull Item... customItems) {
            return this.withCustomItems(Arrays.asList(customItems));
        }

        /**
         * Add {@link Item Items} to the {@link Page} item list.
         *
         * @param customItems Collection of non-field items to add.
         */
        public Builder<T> withCustomItems(@NotNull Iterable<Item> customItems) {
            customItems.forEach(item -> {
                if (!item.isSingular() || !this.customItems.contains(item))
                    this.customItems.add(item);
            });

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
         * Sets the render style for {@link Item PageItems}.
         *
         * @param itemStyle The page item style.
         */
        public Builder<T> withStyle(@NotNull Item.Style itemStyle) {
            this.style = itemStyle;
            return this;
        }

        /**
         * Add a variable to be evaluated when building the dynamic {@link Embed}.
         *
         * @param key The variable name.
         * @param value The variable value.
         */
        public Builder<T> withVariable(@NotNull String key, @NotNull Object value) {
            return this.withVariables(Pair.of(key, value));
        }

        /**
         * Add variables to be evaluated when building the dynamic {@link Embed}.
         *
         * @param variables The variables to be accessible.
         */
        public Builder<T> withVariables(@NotNull Pair<String, Object>... variables) {
            return this.withVariables(Arrays.asList(variables));
        }

        /**
         * Add variables to be evaluated when building the dynamic {@link Embed}.
         *
         * @param variables The variables to be accessible.
         */
        public Builder<T> withVariables(@NotNull Iterable<Pair<String, Object>> variables) {
            variables.forEach(this.variables::put);
            return this;
        }

        /**
         * Add variables to be evaluated when building the dynamic {@link Embed}.
         *
         * @param variables The variables to be accessible.
         */
        public Builder<T> withVariables(@NotNull Map<String, Object> variables) {
            this.variables.putAll(variables);
            return this;
        }

        @Override
        public @NotNull CustomItemHandler<T> build() {
            this.variables.put("SIZE", this.items.size());

            CustomItemHandler<T> itemHandler = new CustomItemHandler<>(
                this.type,
                this.items.toUnmodifiableList(),
                this.sorters,
                this.style,
                this.columnNames,
                this.variables,
                this.customItems,
                this.amountPerPage,
                this.viewerEnabled
            );

            if (ListUtil.notEmpty(this.sorters))
                itemHandler.gotoNextSorter();

            itemHandler.setCacheUpdateRequired();
            return itemHandler;
        }

    }

}
