package dev.sbs.discordapi.response.page;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.response.component.interaction.action.UserActionComponent;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;

public interface Paging {

    ConcurrentList<LayoutComponent<UserActionComponent<?>>> getPageComponents();

    ConcurrentList<Page> getPages();

}
