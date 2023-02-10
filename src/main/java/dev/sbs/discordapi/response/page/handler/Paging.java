package dev.sbs.discordapi.response.page.handler;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;

public interface Paging<T> {

    ConcurrentList<T> getPages();

}
