package dev.sbs.discordapi.response.page;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import org.jetbrains.annotations.NotNull;

public interface Paging<T> {

    @NotNull ConcurrentList<T> getPages();

}
