package dev.sbs.discordapi.listener.message.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.deferrable.component.action.SelectMenuContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;

public final class SelectMenuListener extends ComponentListener<SelectMenuInteractionEvent, SelectMenuContext, SelectMenu> {

    public SelectMenuListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull SelectMenuContext getContext(@NotNull SelectMenuInteractionEvent event, @NotNull Response response, @NotNull SelectMenu component, @NotNull Optional<Response.Cache.Followup> followup) {
        return SelectMenuContext.of(
            this.getDiscordBot(),
            event,
            response,
            component,
            followup
        );
    }

    @Override
    protected Mono<Void> handlePaging(@NotNull SelectMenuContext selectMenuContext) {
        return Mono.just(selectMenuContext)
            .doOnNext(context -> context.getComponent().updateSelected(context.getEvent().getValues()))
            .flatMap(context -> Mono.justOrEmpty(context.getFollowup())
                .map(Response.Cache.Followup::getResponse)
                .switchIfEmpty(Mono.justOrEmpty(context.getResponse()))
                .flatMap(response -> {
                    String selectedValue = context.getSelected().getFirst().orElseThrow().getValue();

                    switch (context.getComponent().getPageType()) {
                        case PAGE -> response.getHistoryHandler().gotoPage(selectedValue);
                        case SUBPAGE -> {
                            if (selectedValue.equals("BACK"))
                                response.getHistoryHandler().gotoPreviousPage();
                            else
                                response.getHistoryHandler().gotoSubPage(selectedValue);
                        }
                        case ITEM -> {
                            /*
                             TODO
                                Build a viewer that converts the item list
                                into something that either lists data about an item
                                or a sub-list from PageItem

                             TODO
                                Viewer will need the ability to enable editing
                                and code to do something on save
                             */
                        }
                    }

                    return context.getResponseCacheEntry().updateResponse(response);
                })
                .then()
            );
    }

}
