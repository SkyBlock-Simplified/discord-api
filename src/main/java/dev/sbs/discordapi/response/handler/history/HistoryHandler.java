package dev.sbs.discordapi.response.handler.history;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.exception.DiscordException;
import dev.sbs.discordapi.response.handler.OutputHandler;
import dev.sbs.discordapi.response.handler.Paging;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
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
public interface HistoryHandler<P, I> extends OutputHandler<P>, Paging<P> {

    @NotNull ConcurrentList<P> getItems();

    @NotNull Optional<BiFunction<P, I, Boolean>> getMatcher();

    @NotNull Optional<Function<P, I>> getTransformer();

    int getMinimumSize();

    //@NotNull P editCurrentPage(@NotNull Function<P, P> page);

    /**
     * Gets an {@link P page} from the provided identifier.
     *
     * @param identifier The identifier to find.
     */
    default @NotNull Optional<P> getPage(I identifier) {
        return this.getItems()
            .stream()
            .filter(page -> this.getMatcher().map(matcher -> matcher.apply(page, identifier)).orElse(false))
            .findFirst();
    }

    @NotNull P getCurrentPage();

    @NotNull Optional<P> getPreviousPage();

    @NotNull ConcurrentList<P> getHistory();

    @NotNull ConcurrentList<I> getIdentifierHistory();

    boolean hasPageHistory();

    /**
     * Changes the current {@link P page} to a top-level page using the given identifier.
     *
     * @param identifier The page option value.
     */
    default void locatePage(@NotNull I identifier) {
        this.gotoPage(this.getPage(identifier).orElseThrow(() -> new DiscordException("Unable to locate page identified by '%s'.", identifier)));
    }

    static <P, I> @NotNull Builder_old<P, I> builder() {
        return new Builder_old<>();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    class Builder_old<P, I> implements dev.sbs.api.util.builder.Builder<HistoryHandler<P, I>> {

        private final ConcurrentList<P> pages = Concurrent.newList();
        private Optional<Function<P, I>> historyTransformer = Optional.empty();
        private Optional<BiFunction<P, I, Boolean>> historyMatcher = Optional.empty();
        private int minimumSize = 1;

        public Builder_old<P, I> withHistoryMatcher(@Nullable BiFunction<P, I, Boolean> transformer) {
            return this.withHistoryMatcher(Optional.ofNullable(transformer));
        }

        public Builder_old<P, I> withHistoryMatcher(@NotNull Optional<BiFunction<P, I, Boolean>> transformer) {
            this.historyMatcher = transformer;
            return this;
        }


        public Builder_old<P, I> withHistoryTransformer(@Nullable Function<P, I> transformer) {
            return this.withHistoryTransformer(Optional.ofNullable(transformer));
        }

        public Builder_old<P, I> withHistoryTransformer(@NotNull Optional<Function<P, I>> transformer) {
            this.historyTransformer = transformer;
            return this;
        }

        public Builder_old<P, I> withMinimumSize(int value) {
            this.minimumSize = Math.max(value, 0);
            return this;
        }

        /**
         * Add pages to the {@link HistoryHandler}.
         *
         * @param pages Variable number of pages to add.
         */
        public Builder_old<P, I> withPages(@NotNull P... pages) {
            return this.withPages(Arrays.asList(pages));
        }

        /**
         * Add pages to the {@link HistoryHandler}.
         *
         * @param pages Collection of pages to add.
         */
        public Builder_old<P, I> withPages(@NotNull Iterable<P> pages) {
            pages.forEach(this.pages::add);
            return this;
        }

        @Override
        public abstract @NotNull HistoryHandler<P, I> build();

    }

}
