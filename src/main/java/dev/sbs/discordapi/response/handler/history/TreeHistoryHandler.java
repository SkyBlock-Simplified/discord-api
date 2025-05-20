package dev.sbs.discordapi.response.handler.history;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.NumberUtil;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.exception.DiscordException;
import dev.sbs.discordapi.response.page.Subpages;
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
public class TreeHistoryHandler<P extends Subpages<P>, I> implements HistoryHandler<P, I> {

    private final @NotNull ConcurrentList<P> items;
    private final @NotNull Optional<BiFunction<P, I, Boolean>> matcher;
    private final @NotNull Optional<Function<P, I>> transformer;
    @Setter private boolean cacheUpdateRequired;
    @Getter(AccessLevel.NONE)
    private final @NotNull ConcurrentList<P> history = Concurrent.newList();

    public static <P extends Subpages<P>, I> @NotNull Builder<P, I> builder() {
        return new Builder<>();
    }

    @Override
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

        TreeHistoryHandler<?, ?> that = (TreeHistoryHandler<?, ?>) o;

        return new EqualsBuilder()
            .append(this.isCacheUpdateRequired(), that.isCacheUpdateRequired())
            .append(this.getItems(), that.getItems())
            .append(this.getMatcher(), that.getMatcher())
            .append(this.getTransformer(), that.getTransformer())
            .append(this.getHistory(), that.getHistory())
            .build();
    }

    /**
     * Gets an {@link P page} from the provided identifier.
     *
     * @param identifier The identifier to find.
     */
    public @NotNull Optional<P> getPage(I identifier) {
        return this.getItems()
            .stream()
            .filter(page -> this.getMatcher().map(matcher -> matcher.apply(page, identifier)).orElse(false))
            .findFirst();
    }

    @Override
    public int getCurrentIndex() {
        throw new UnsupportedOperationException("TreeHistoryHandler does not support indexed paging.");
    }

    public @NotNull P getCurrentPage() {
        return this.history.getLast().orElseThrow(); // Will Always Exist
    }

    public @NotNull Optional<P> getPreviousPage() {
        return Optional.ofNullable(this.history.size() > 1 ? this.history.get(this.history.size() - 2) : null);
    }

    public @NotNull ConcurrentList<P> getHistory() {
        return this.history.toUnmodifiableList();
    }

    public @NotNull ConcurrentList<I> getIdentifierHistory() {
        return this.history.stream()
            .map(page -> this.getTransformer().map(transformer -> transformer.apply(page)))
            .flatMap(Optional::stream)
            .collect(Concurrent.toList());
    }

    /**
     * Gets a {@link P subpage} from the {@link P CurrentPage}.
     *
     * @param identifier The subpage option value.
     */
    public @NotNull Optional<P> getSubPage(I identifier) {
        return this.getCurrentPage()
            .getPages()
            .stream()
            .filter(page -> this.getMatcher().map(matcher -> matcher.apply(page, identifier)).orElse(false))
            .findFirst();
    }

    @Override
    public int getTotalPages() {
        return this.getCurrentPage().getPages().size();
    }

    /**
     * Changes the current {@link P page} to a top-level page.
     *
     * @param page The page value.
     */
    @Override
    public void gotoPage(@NotNull P page) {
        this.history.clear();
        this.history.add(page);
        this.setCacheUpdateRequired();
    }

    @Override
    public void gotoNextPage() {
        int index = this.getCurrentPage().getPages().indexOf(this.getCurrentPage());
        int next = NumberUtil.ensureRange(index, 0, this.getCurrentPage().getPages().size() - 1);
        this.history.add(this.getCurrentPage().getPages().get(next));
    }

    @Override
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
            .append(this.getItems())
            .append(this.getMatcher())
            .append(this.getTransformer())
            .append(this.isCacheUpdateRequired())
            .append(this.getHistory())
            .build();
    }

    @Override
    public boolean hasPageHistory() {
        return this.history.size() > 1;
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<P extends Subpages<P>, I> implements dev.sbs.api.util.builder.Builder<TreeHistoryHandler<P, I>> {

        private final ConcurrentList<P> pages = Concurrent.newList();
        private Optional<Function<P, I>> transformer = Optional.empty();
        private Optional<BiFunction<P, I, Boolean>> matcher = Optional.empty();

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

        /**
         * Add pages to the {@link TreeHistoryHandler}.
         *
         * @param pages Variable number of pages to add.
         */
        public Builder<P, I> withPages(@NotNull P... pages) {
            return this.withPages(Arrays.asList(pages));
        }

        /**
         * Add pages to the {@link TreeHistoryHandler}.
         *
         * @param pages Collection of pages to add.
         */
        public Builder<P, I> withPages(@NotNull Iterable<P> pages) {
            pages.forEach(this.pages::add);
            return this;
        }

        @Override
        public @NotNull TreeHistoryHandler<P, I> build() {
            return new TreeHistoryHandler<>(
                this.pages,
                this.matcher,
                this.transformer
            );
        }

    }

}
