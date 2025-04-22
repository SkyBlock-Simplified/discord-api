package dev.sbs.discordapi.response.page;

import org.jetbrains.annotations.NotNull;

public interface Paging<T> {

    @NotNull T getCurrentPage();

    int getTotalPages();

    void gotoPage(@NotNull T identifier);

    void gotoNextPage();

    void gotoPreviousPage();

}
