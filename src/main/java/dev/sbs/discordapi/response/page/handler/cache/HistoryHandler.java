package dev.sbs.discordapi.response.page.handler.cache;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.exception.DiscordException;
import dev.sbs.discordapi.response.page.Paging;
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

/**
 * A page handling wrapper.
 *
 * @param <P> Page type for history.
 * @param <I> Identifier type for searching.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class HistoryHandler<P extends Paging<P>, I> implements CacheHandler {

    private final ConcurrentList<P> pages;
    private final Optional<BiFunction<P, I, Boolean>> historyMatcher;
    private final Optional<Function<P, I>> historyTransformer;
    private final int minimumSize;
    @Setter private boolean cacheUpdateRequired;
    @Getter(AccessLevel.NONE)
    private final ConcurrentList<P> history = Concurrent.newList();

    public static <P extends Paging<P>, I> Builder<P, I> builder() {
        return new Builder<>();
    }

    public @NotNull P editCurrentPage(@NotNull Function<P, P> page) {
        P newPage = page.apply(this.getCurrentPage());
        this.history.set(this.history.size() - 1, newPage);
        this.setCacheUpdateRequired();
        return newPage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HistoryHandler<?, ?> that = (HistoryHandler<?, ?>) o;

        return new EqualsBuilder()
            .append(this.getMinimumSize(), that.getMinimumSize())
            .append(this.isCacheUpdateRequired(), that.isCacheUpdateRequired())
            .append(this.getPages(), that.getPages())
            .append(this.getHistoryMatcher(), that.getHistoryMatcher())
            .append(this.getHistoryTransformer(), that.getHistoryTransformer())
            .append(this.getHistory(), that.getHistory())
            .build();
    }

    /**
     * Gets an {@link P page} from the provided identifier.
     *
     * @param identifier The identifier to find.
     */
    public Optional<P> getPage(I identifier) {
        return this.getPages()
            .stream()
            .filter(page -> this.getHistoryMatcher().map(matcher -> matcher.apply(page, identifier)).orElse(false))
            .findFirst();
    }

    public @NotNull P getCurrentPage() {
        return this.history.getLast().orElseThrow(); // Will Always Exist
    }

    public Optional<P> getPreviousPage() {
        return Optional.ofNullable(this.history.size() > 1 ? this.history.get(this.history.size() - 2) : null);
    }

    public @NotNull ConcurrentList<P> getHistory() {
        return this.history.toUnmodifiableList();
    }

    public @NotNull ConcurrentList<I> getHistoryIdentifiers() {
        return this.history.stream()
            .map(page -> this.getHistoryTransformer().map(transformer -> transformer.apply(page)))
            .flatMap(Optional::stream)
            .collect(Concurrent.toList());
    }

    /**
     * Gets a {@link P subpage} from the {@link P CurrentPage}.
     *
     * @param identifier The subpage option value.
     */
    public Optional<P> getSubPage(I identifier) {
        return this.getCurrentPage()
            .getPages()
            .stream()
            .filter(page -> this.getHistoryMatcher().map(matcher -> matcher.apply(page, identifier)).orElse(false))
            .findFirst();
    }

    /**
     * Changes the current {@link P page} to a top-level page using the given identifier.
     *
     * @param identifier The page option value.
     */
    public void gotoPage(I identifier) {
        this.gotoPage(this.getPage(identifier).orElseThrow(() -> new DiscordException("Unable to locate page identified by '%s'.", identifier)));
    }

    /**
     * Changes the current {@link P page} to a top-level page.
     *
     * @param page The page value.
     */
    public void gotoPage(P page) {
        this.history.clear();
        this.history.add(page);
        this.setCacheUpdateRequired();
    }

    public void gotoPreviousPage() {
        if (this.hasPageHistory())
            this.history.removeLast();
    }

    /**
     * Changes the current {@link P page} to a subpage of the current {@link P page} using the given identifier.
     *
     * @param identifier The subpage option value.
     */
    public void gotoSubPage(I identifier) {
        this.history.add(this.getSubPage(identifier).orElseThrow(() -> new DiscordException("Unable to locate subpage identified by '%s'.", identifier)));
        this.setCacheUpdateRequired();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getPages())
            .append(this.getHistoryMatcher())
            .append(this.getHistoryTransformer())
            .append(this.getMinimumSize())
            .append(this.isCacheUpdateRequired())
            .append(this.getHistory())
            .build();
    }

    public boolean hasPageHistory() {
        return this.history.size() > this.getMinimumSize();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<P extends Paging<P>, I> implements dev.sbs.api.util.builder.Builder<HistoryHandler<P, I>> {

        private final ConcurrentList<P> pages = Concurrent.newList();
        private Optional<Function<P, I>> historyTransformer = Optional.empty();
        private Optional<BiFunction<P, I, Boolean>> historyMatcher = Optional.empty();
        private int minimumSize = 1;

        public Builder<P, I> withHistoryMatcher(@Nullable BiFunction<P, I, Boolean> transformer) {
            return this.withHistoryMatcher(Optional.ofNullable(transformer));
        }

        public Builder<P, I> withHistoryMatcher(@NotNull Optional<BiFunction<P, I, Boolean>> transformer) {
            this.historyMatcher = transformer;
            return this;
        }


        public Builder<P, I> withHistoryTransformer(@Nullable Function<P, I> transformer) {
            return this.withHistoryTransformer(Optional.ofNullable(transformer));
        }

        public Builder<P, I> withHistoryTransformer(@NotNull Optional<Function<P, I>> transformer) {
            this.historyTransformer = transformer;
            return this;
        }

        public Builder<P, I> withMinimumSize(int value) {
            this.minimumSize = Math.max(value, 0);
            return this;
        }

        /**
         * Add pages to the {@link HistoryHandler}.
         *
         * @param pages Variable number of pages to add.
         */
        public Builder<P, I> withPages(@NotNull P... pages) {
            return this.withPages(Arrays.asList(pages));
        }

        /**
         * Add pages to the {@link HistoryHandler}.
         *
         * @param pages Collection of pages to add.
         */
        public Builder<P, I> withPages(@NotNull Iterable<P> pages) {
            pages.forEach(this.pages::add);
            return this;
        }

        @Override
        public @NotNull HistoryHandler<P, I> build() {
            return new HistoryHandler<>(
                this.pages.toUnmodifiableList(),
                this.historyMatcher,
                this.historyTransformer,
                this.minimumSize
            );
        }

    }

}
