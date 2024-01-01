package dev.sbs.discordapi.listener.message.component;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.deferrable.component.action.ButtonContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.Modal;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.response.component.layout.ActionRow;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.handler.search.Search;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;

public final class ButtonListener extends ComponentListener<ButtonInteractionEvent, ButtonContext, Button> {

    public ButtonListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull ButtonContext getContext(@NotNull ButtonInteractionEvent event, @NotNull Response response, @NotNull Button component, @NotNull Optional<Response.Cache.Followup> followup) {
        return ButtonContext.of(
            this.getDiscordBot(),
            event,
            response,
            component,
            followup
        );
    }

    @Override
    protected Mono<Void> handlePaging(@NotNull ButtonContext context) {
        return Mono.justOrEmpty(context.getFollowup())
            .map(Response.Cache.Followup::getResponse)
            .switchIfEmpty(Mono.justOrEmpty(context.getResponse()))
            .flatMap(response -> {
                Page currentPage = response.getHistoryHandler().getCurrentPage();

                switch (context.getComponent().getPageType()) {
                    case FIRST -> currentPage.getItemHandler().gotoFirstItemPage();
                    case PREVIOUS -> currentPage.getItemHandler().gotoPreviousItemPage();
                    case NEXT -> currentPage.getItemHandler().gotoNextItemPage();
                    case LAST -> currentPage.getItemHandler().gotoLastItemPage();
                    case BACK -> currentPage.getHistoryHandler().gotoPreviousPage();
                    case SEARCH -> {
                        return context.presentModal(
                            Modal.builder()
                                .withComponents(
                                    currentPage.getItemHandler()
                                        .getSearchers()
                                        .stream()
                                        .map(Search::getTextInput)
                                        .map(ActionRow::of)
                                        .collect(Concurrent.toUnmodifiableList())
                                )
                                .withTitle("Search")
                                .withPageType(Modal.PageType.SEARCH)
                                .build()
                        );
                    }
                    case SORT -> currentPage.getItemHandler().getSortHandler().gotoNext();
                    case ORDER -> currentPage.getItemHandler().getSortHandler().invertOrder();
                }

                return context.getResponseCacheEntry().updateResponse(response);
            })
            .then();
    }

}
