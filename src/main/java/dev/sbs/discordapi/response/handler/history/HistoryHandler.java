package dev.sbs.discordapi.response.handler.history;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.exception.DiscordException;
import dev.sbs.discordapi.response.handler.OutputHandler;
import dev.sbs.discordapi.response.handler.Paging;
import org.jetbrains.annotations.NotNull;

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

}
