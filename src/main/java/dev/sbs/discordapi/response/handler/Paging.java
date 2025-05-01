package dev.sbs.discordapi.response.handler;

import org.jetbrains.annotations.NotNull;

public interface Paging<T> {

    @NotNull T getCurrentPage();

    int getCurrentIndex();

    int getTotalPages();

    void gotoPage(@NotNull T identifier);

    void gotoNextPage();

    void gotoPreviousPage();

    default boolean hasPageHistory() {
        return this.getCurrentIndex() > 0;
    }

}
