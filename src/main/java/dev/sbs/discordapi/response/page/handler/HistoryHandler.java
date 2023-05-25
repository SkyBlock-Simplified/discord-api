package dev.sbs.discordapi.response.page.handler;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.discordapi.response.page.Paging;
import dev.sbs.discordapi.util.exception.DiscordException;
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
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class HistoryHandler<P extends Paging<P>, I> implements CacheHandler {

    @Getter private final ConcurrentList<P> pages;
    @Getter private final Optional<BiFunction<P, I, Boolean>> historyMatcher;
    @Getter private final Optional<Function<P, I>> historyTransformer;
    @Getter private final int minimumSize;
    @Getter @Setter private boolean cacheUpdateRequired;
    private final ConcurrentList<P> history = Concurrent.newList();

    public static <P extends Paging<P>, I> Builder<P, I> builder() {
        return new Builder<>();
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

    public P getCurrentPage() {
        return this.history.getLast().orElseThrow(); // Will Always Exist
    }

    public Optional<P> getPreviousPage() {
        return Optional.ofNullable(this.history.size() > 1 ? this.history.get(this.history.size() - 2) : null);
    }

    public ConcurrentList<P> getHistory() {
        return this.history.toUnmodifiableList();
    }

    public ConcurrentList<I> getHistoryIdentifiers() {
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
        this.gotoPage(this.getPage(identifier).orElseThrow(
            () -> SimplifiedException.of(DiscordException.class)
                .withMessage("Unable to locate page identified by ''{0}''!", identifier)
                .build()
        ));
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
        this.history.add(this.getSubPage(identifier).orElseThrow(
            () -> SimplifiedException.of(DiscordException.class)
                .withMessage("Unable to locate subpage identified by ''{0}''!", identifier)
                .build()
        ));
        this.setCacheUpdateRequired();
    }

    public boolean hasPageHistory() {
        return ListUtil.sizeOf(this.history) > this.getMinimumSize();
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
        public HistoryHandler<P, I> build() {
            return new HistoryHandler<>(
                this.pages.toUnmodifiableList(),
                this.historyMatcher,
                this.historyTransformer,
                this.minimumSize
            );
        }

    }

}
