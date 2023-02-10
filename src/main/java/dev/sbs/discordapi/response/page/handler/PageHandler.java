package dev.sbs.discordapi.response.page.handler;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.util.exception.DiscordException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class PageHandler<P extends Paging<P>, I> extends Handler<P, I> {

    protected PageHandler(
        @NotNull ConcurrentList<P> pages,
        @NotNull Optional<Function<P, I>> historyTransformer,
        @NotNull Optional<BiFunction<P, I, Boolean>> historyMatcher) {
        super(pages, historyTransformer, historyMatcher);
    }

    public static <P extends Paging<P>, I> Builder<P, I> builder() {
        return new Builder<>();
    }

    /**
     * Gets an {@link P page} from the provided identifier.
     *
     * @param identifier The identifier to find.
     */
    public final Optional<P> getPage(I identifier) {
        return this.getPages()
            .stream()
            .filter(page -> this.getHistoryMatcher().map(matcher -> matcher.apply(page, identifier)).orElse(false))
            .findFirst();
    }

    /**
     * Changes the current {@link P page} to a top-level page using the given identifier.
     *
     * @param identifier The page option value.
     */
    public final void gotoPage(I identifier) {
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
    public final void gotoPage(P page) {
        this.history.clear();
        this.history.add(page);
        this.setCacheUpdateRequired();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<P extends Paging<P>, I> implements dev.sbs.api.util.builder.Builder<PageHandler<P, I>> {

        private final ConcurrentList<P> pages = Concurrent.newList();
        private Optional<Function<P, I>> historyTransformer = Optional.empty();
        private Optional<BiFunction<P, I, Boolean>> historyMatcher = Optional.empty();


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

        /**
         * Add pages to the {@link PageHandler}.
         *
         * @param pages Variable number of pages to add.
         */
        public Builder<P, I> withPages(@NotNull P... pages) {
            return this.withPages(Arrays.asList(pages));
        }

        /**
         * Add pages to the {@link PageHandler}.
         *
         * @param pages Collection of pages to add.
         */
        public Builder<P, I> withPages(@NotNull Iterable<P> pages) {
            pages.forEach(this.pages::add);
            return this;
        }

        @Override
        public PageHandler<P, I> build() {
            return new PageHandler<>(
                this.pages.toUnmodifiableList(),
                this.historyTransformer,
                this.historyMatcher
            );
        }

    }

}
