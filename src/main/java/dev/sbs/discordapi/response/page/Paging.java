package dev.sbs.discordapi.response.page;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;

public interface Paging {

    ConcurrentList<LayoutComponent<?>> getPageComponents();

    ConcurrentList<Page> getPages();

}
