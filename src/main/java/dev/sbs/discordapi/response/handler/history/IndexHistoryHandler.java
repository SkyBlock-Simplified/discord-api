package dev.sbs.discordapi.response.handler.history;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.NumberUtil;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.exception.DiscordException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * A page handling wrapper.
 *
 * @param <P> Page type for history.
 * @param <I> Identifier type for searching.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class IndexHistoryHandler<P, I> implements HistoryHandler<P, I> {

    private final @NotNull ConcurrentList<P> items;
    private final @NotNull Optional<BiFunction<P, I, Boolean>> matcher;
    private final @NotNull Optional<Function<P, I>> transformer;
    private final int minimumSize;
    @Setter private boolean cacheUpdateRequired;
    @Getter(AccessLevel.PRIVATE)
    private int currentIndex = 0;

    public static <P, I> @NotNull Builder<P, I> builder() {
        return new Builder<>();
    }

    /*public @NotNull P editCurrentPage(@NotNull Function<P, P> page) {
        P newPage = page.apply(this.getCurrentPage());
        this.history.set(this.history.size() - 1, newPage);
        this.setCacheUpdateRequired();
        return newPage;
    }*/

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IndexHistoryHandler<?, ?> that = (IndexHistoryHandler<?, ?>) o;

        return new EqualsBuilder()
            .append(this.getMinimumSize(), that.getMinimumSize())
            .append(this.isCacheUpdateRequired(), that.isCacheUpdateRequired())
            .append(this.getItems(), that.getItems())
            .append(this.getMatcher(), that.getMatcher())
            .append(this.getTransformer(), that.getTransformer())
            .append(this.getHistory(), that.getHistory())
            .build();
    }

    @Override
    public @NotNull P getCurrentPage() {
        return this.getItems().get(this.getCurrentIndex()); // Will Always Exist
    }

    @Override
    public @NotNull ConcurrentList<P> getHistory() {
        return this.getItems().subList(0, this.getCurrentIndex() + 1);
    }

    @Override
    public @NotNull ConcurrentList<I> getIdentifierHistory() {
        return IntStream.range(0, this.getCurrentIndex())
            .mapToObj(index -> this.getItems().get(index))
            .map(page -> this.getTransformer().map(transformer -> transformer.apply(page)))
            .flatMap(Optional::stream)
            .collect(Concurrent.toList());
    }

    /**
     * Gets an {@link P page} from the provided identifier.
     *
     * @param identifier The identifier to find.
     */
    @Override
    public @NotNull Optional<P> getPage(I identifier) {
        return this.getItems()
            .stream()
            .filter(page -> this.getMatcher().map(matcher -> matcher.apply(page, identifier)).orElse(false))
            .findFirst();
    }

    @Override
    public @NotNull Optional<P> getPreviousPage() {
        return Optional.ofNullable(this.hasPageHistory() ? this.getItems().get(this.getCurrentIndex() - 1) : null);
    }

    @Override
    public int getTotalPages() {
        return this.getItems().size();
    }

    /**
     * Changes the current {@link P page} to a top-level page using the given identifier.
     *
     * @param identifier The page option value.
     */
    public void locatePage(@NotNull I identifier) {
        this.gotoPage(this.getPage(identifier).orElseThrow(() -> new DiscordException("Unable to locate page identified by '%s'.", identifier)));
    }

    /**
     * Changes the current {@link P page} to a top-level page.
     *
     * @param page The page value.
     */
    @Override
    public void gotoPage(@NotNull P page) {
        this.currentIndex = this.getItems().indexOf(page);
        this.setCacheUpdateRequired();
    }

    @Override
    public void gotoNextPage() {
        this.currentIndex = NumberUtil.ensureRange(this.getCurrentIndex() + 1, 0, this.getItems().size() - 1);
        this.setCacheUpdateRequired();
    }

    @Override
    public void gotoPreviousPage() {
        this.currentIndex = NumberUtil.ensureRange(this.getCurrentIndex() + 1, 0, this.getItems().size() - 1);
        this.setCacheUpdateRequired();
    }

    @Override
    public boolean hasPageHistory() {
        return this.getCurrentIndex() > 0;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getItems())
            .append(this.getMatcher())
            .append(this.getTransformer())
            .append(this.getMinimumSize())
            .append(this.isCacheUpdateRequired())
            .append(this.getHistory())
            .build();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<P, I> implements dev.sbs.api.util.builder.Builder<IndexHistoryHandler<P, I>> {

        private final ConcurrentList<P> pages = Concurrent.newList();
        private Optional<Function<P, I>> transformer = Optional.empty();
        private Optional<BiFunction<P, I, Boolean>> matcher = Optional.empty();
        private int minimumSize = 1;

        public Builder<P, I> withMatcher(@Nullable BiFunction<P, I, Boolean> transformer) {
            return this.withMatcher(Optional.ofNullable(transformer));
        }

        public Builder<P, I> withMatcher(@NotNull Optional<BiFunction<P, I, Boolean>> transformer) {
            this.matcher = transformer;
            return this;
        }


        public Builder<P, I> withTransformer(@Nullable Function<P, I> transformer) {
            return this.withTransformer(Optional.ofNullable(transformer));
        }

        public Builder<P, I> withTransformer(@NotNull Optional<Function<P, I>> transformer) {
            this.transformer = transformer;
            return this;
        }

        public Builder<P, I> withMinimumSize(int value) {
            this.minimumSize = Math.max(value, 0);
            return this;
        }

        /**
         * Add pages to the {@link IndexHistoryHandler}.
         *
         * @param pages Variable number of pages to add.
         */
        public Builder<P, I> withPages(@NotNull P... pages) {
            return this.withPages(Arrays.asList(pages));
        }

        /**
         * Add pages to the {@link IndexHistoryHandler}.
         *
         * @param pages Collection of pages to add.
         */
        public Builder<P, I> withPages(@NotNull Iterable<P> pages) {
            pages.forEach(this.pages::add);
            return this;
        }

        @Override
        public @NotNull IndexHistoryHandler<P, I> build() {
            return new IndexHistoryHandler<>(
                this.pages.toUnmodifiableList(),
                this.matcher,
                this.transformer,
                this.minimumSize
            );
        }

    }

}
