package dev.sbs.discordapi.response.page.history;

import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.NumberUtil;
import dev.sbs.discordapi.response.page.item.PageItem;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ItemHandler<T, P extends Paging<P>, I> extends Handler<P, I> {

    @Getter private final @NotNull ConcurrentList<T> items;
    @Getter private final int amountPerPage;
    @Getter private int currentItemPage = 1;
    private ConcurrentList<PageItem> cachedPageItems = Concurrent.newUnmodifiableList();

    protected ItemHandler(
        @NotNull ConcurrentList<P> pages,
        @NotNull Optional<Function<P, I>> historyTransformer,
        @NotNull Optional<BiFunction<P, I, Boolean>> historyMatcher,
        @NotNull ConcurrentList<T> items,
        int amountPerPage) {
        super(pages, historyTransformer, historyMatcher);
        this.items = items.toUnmodifiableList();
        this.amountPerPage = amountPerPage;
    }

    public static <T, P extends Paging<P>, I> Builder<T, P, I> builder() {
        return new Builder<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ItemHandler<?, ?, ?> that = (ItemHandler<?, ?, ?>) o;

        return new EqualsBuilder()
            .append(this.getAmountPerPage(), that.getAmountPerPage())
            .append(this.getCurrentItemPage(), that.getCurrentItemPage())
            .append(this.getItems(), that.getItems())
            .build();
    }

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

    public final void gotoPreviousItemPage() {
        this.gotoItemPage(this.currentItemPage - 1);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(this.getItems())
            .append(this.getAmountPerPage())
            .append(this.getCurrentItemPage())
            .build();
    }

    public final boolean hasNextItemPage() {
        return this.currentItemPage < this.getTotalItemPages();
    }

    public final boolean hasPreviousItemPage() {
        return this.currentItemPage > 1;
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<T, P extends Paging<P>, I> implements dev.sbs.api.util.builder.Builder<ItemHandler<T, P, I>> {

        private final ConcurrentList<P> pages = Concurrent.newList();
        private Optional<Function<P, I>> historyTransformer = Optional.empty();
        private Optional<BiFunction<P, I, Boolean>> historyMatcher = Optional.empty();
        private final ConcurrentList<T> items = Concurrent.newList();
        private int amountPerPage = 12;

        /**
         * Sets the amount of page items to render per {@link P page}.
         * <br><br>
         * Defaults to 12.<br>
         * Minimum required is 1.<br>
         * Maximum allowed is 24.
         *
         * @param amountPerPage Number of items to render.
         */
        public Builder<T, P, I> withAmountPerPage(int amountPerPage) {
            this.amountPerPage = NumberUtil.ensureRange(amountPerPage, 1, 24);
            return this;
        }


        public Builder<T, P, I> withHistoryMatcher(@Nullable BiFunction<P, I, Boolean> transformer) {
            return this.withHistoryMatcher(Optional.ofNullable(transformer));
        }

        public Builder<T, P, I> withHistoryMatcher(@NotNull Optional<BiFunction<P, I, Boolean>> transformer) {
            this.historyMatcher = transformer;
            return this;
        }


        public Builder<T, P, I> withHistoryTransformer(@Nullable Function<P, I> transformer) {
            return this.withHistoryTransformer(Optional.ofNullable(transformer));
        }

        public Builder<T, P, I> withHistoryTransformer(@NotNull Optional<Function<P, I>> transformer) {
            this.historyTransformer = transformer;
            return this;
        }

        /**
         * Add pages to the {@link ItemHandler}.
         *
         * @param pages Variable number of pages to add.
         */
        public Builder<T, P, I> withPages(@NotNull P... pages) {
            return this.withPages(Arrays.asList(pages));
        }

        /**
         * Add pages to the {@link ItemHandler}.
         *
         * @param pages Collection of pages to add.
         */
        public Builder<T, P, I> withPages(@NotNull Iterable<P> pages) {
            pages.forEach(this.pages::add);
            return this;
        }

        @Override
        public ItemHandler<T, P, I> build() {
            return new ItemHandler<>(
                this.pages.toUnmodifiableList(),
                this.historyTransformer,
                this.historyMatcher,
                this.items,
                this.amountPerPage
            );
        }

    }

}
