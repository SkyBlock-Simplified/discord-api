package dev.simplified.discordapi.handler.response;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.discordapi.response.Response;
import dev.simplified.discordapi.response.handler.HistoryHandler;
import dev.simplified.discordapi.response.page.Page;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/**
 * Serializable snapshot of a {@link Response Response}'s
 * mutable navigation state.
 *
 * <p>
 * Captures the current page identifier, the current paginated item index, and
 * the ordered history of visited page identifiers used by back-navigation.
 */
@Getter
@RequiredArgsConstructor
public final class NavState implements Serializable {

    /** Identifier of the page currently displayed, if any. */
    private final @NotNull Optional<String> currentPageId;

    /** Zero-based index into the current page's paginated item list. */
    private final int currentItemPage;

    /** Ordered history of visited page identifiers for back-navigation. */
    private final @NotNull ConcurrentList<String> pageHistory;

    /** Returns an empty navigation state used as the default for new responses. */
    public static @NotNull NavState empty() {
        return new NavState(Optional.empty(), 0, Concurrent.newList());
    }

    /**
     * Captures the navigation coordinate of the given handler: the breadcrumb of visited page identifiers
     * (root to current) and the current page's item-pagination index.
     *
     * @param handler the history handler to snapshot
     * @return the captured navigation state
     */
    public static @NotNull NavState capture(@NotNull HistoryHandler<Page, String> handler) {
        ConcurrentList<String> history = handler.getIdentifierHistory();
        Optional<String> current = history.isEmpty() ? Optional.empty() : Optional.of(history.getLast());
        return new NavState(current, handler.getCurrentPage().getItemHandler().getCurrentIndex(), history);
    }

    /**
     * Restores this coordinate onto a freshly built handler by replaying the breadcrumb: goes to the first
     * identifier as a top-level page, re-enters each remaining identifier as a subpage, then restores the
     * item page. Tolerates a stale identifier (a page removed or renamed since capture) by stopping at the
     * deepest page that still resolves. An empty coordinate opens the first page.
     *
     * @param handler the freshly built history handler to navigate
     */
    public void applyTo(@NotNull HistoryHandler<Page, String> handler) {
        ConcurrentList<String> trail = Concurrent.newList(this.pageHistory);

        if (trail.isEmpty() || handler.getPage(trail.getFirst()).isEmpty()) {
            handler.gotoPage(handler.getItems().getFirst());
            return;
        }

        handler.gotoTopLevelPage(trail.removeFirst());

        for (String identifier : trail) {
            if (handler.getSubPage(identifier).isEmpty())
                break;

            handler.gotoSubPage(identifier);
        }

        Page current = handler.getCurrentPage();

        if (current.hasItems())
            current.getItemHandler().gotoPage(this.currentItemPage);
    }

    /** Returns a copy of this state with the given current page id. */
    public @NotNull NavState withCurrentPageId(@NotNull String pageId) {
        ConcurrentList<String> history = Concurrent.newList(this.pageHistory);
        return new NavState(Optional.of(pageId), this.currentItemPage, history);
    }

    /** Returns a copy with the given item page index. */
    public @NotNull NavState withCurrentItemPage(int itemPage) {
        ConcurrentList<String> history = Concurrent.newList(this.pageHistory);
        return new NavState(this.currentPageId, itemPage, history);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NavState other)) return false;
        return this.currentItemPage == other.currentItemPage
            && this.currentPageId.equals(other.currentPageId)
            && this.pageHistory.equals(other.pageHistory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.currentPageId, this.currentItemPage, this.pageHistory);
    }

}
