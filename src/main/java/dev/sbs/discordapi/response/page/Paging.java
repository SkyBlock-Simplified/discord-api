package dev.sbs.discordapi.response.page;

import org.jetbrains.annotations.NotNull;

/**
 * Navigation contract for a paginated collection.
 *
 * @param <T> the page identifier type
 */
public interface Paging<T> {

    /** The current page identifier. */
    @NotNull T getCurrentPage();

    /** The current zero-based or one-based page index, depending on the implementation. */
    int getCurrentIndex();

    /** The total number of pages. */
    int getTotalPages();

    /**
     * Navigates to the page identified by the given value.
     *
     * @param identifier the page identifier
     */
    void gotoPage(@NotNull T identifier);

    /** Navigates to the next page. */
    void gotoNextPage();

    /** Navigates to the previous page. */
    void gotoPreviousPage();

    /** Whether there is page history to navigate back through. */
    default boolean hasPageHistory() {
        return this.getCurrentIndex() > 0;
    }

}
