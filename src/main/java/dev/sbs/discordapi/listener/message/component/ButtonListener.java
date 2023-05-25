package dev.sbs.discordapi.listener.message.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.interaction.deferrable.component.action.button.ButtonContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.response.page.Page;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

public final class ButtonListener extends ComponentListener<ButtonInteractionEvent, ButtonContext, Button> {

    public ButtonListener(DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected ButtonContext getContext(@NotNull ButtonInteractionEvent event, @NotNull Response response, @NotNull Button component) {
        return ButtonContext.of(this.getDiscordBot(), event, response, component);
    }

    @Override
    protected Mono<Void> handlePaging(@NotNull ButtonContext context) {
        return Mono.justOrEmpty(context.getResponse())
            .doOnNext(response -> {
                Page currentPage = response.getHistoryHandler().getCurrentPage();

                switch (context.getComponent().getPageType()) {
                    case FIRST -> currentPage.getItemHandler().gotoFirstItemPage();
                    case LAST -> currentPage.getItemHandler().gotoLastItemPage();
                    case NEXT -> currentPage.getItemHandler().gotoNextItemPage();
                    case PREVIOUS -> currentPage.getItemHandler().gotoPreviousItemPage();
                    case BACK -> currentPage.getHistoryHandler().gotoPreviousPage();
                    // TODO: SEARCH BUTTON
                    case SORT -> currentPage.getItemHandler().gotoNextSorter();
                    case ORDER -> currentPage.getItemHandler().invertOrder();
                }

                context.getResponseCacheEntry().updateResponse(response);
            })
            .then();
    }

}
