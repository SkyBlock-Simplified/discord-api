package dev.sbs.discordapi.response.handler;

import org.jetbrains.annotations.NotNull;

public interface Paging<T> {

    @NotNull T getCurrentPage();

    int getCurrentIndex();

    int getTotalPages();

    /**
     * Changes the current top-level page to the provided page.
     *
     * @param identifier The page value.
     */
    void gotoPage(@NotNull T identifier);

    void gotoNextPage();

    void gotoPreviousPage();

    default boolean hasPageHistory() {
        return this.getCurrentIndex() > 0;
    }

}
