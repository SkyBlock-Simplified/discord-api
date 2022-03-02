package dev.sbs.discordapi.listener.message.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.message.interaction.component.button.ButtonContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.action.Button;
import dev.sbs.discordapi.response.page.Page;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import reactor.core.publisher.Mono;

public final class ButtonListener extends ComponentListener<ButtonInteractionEvent, ButtonContext, Button> {

    public ButtonListener(DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected ButtonContext getContext(ButtonInteractionEvent event, Response response, Button component) {
        return ButtonContext.of(this.getDiscordBot(), event, response, component);
    }

    @Override
    protected Mono<Void> handlePaging(ButtonContext context) {
        return Mono.justOrEmpty(context.getResponse())
            .doOnNext(response -> {
                Page currentPage = response.getCurrentPage();

                switch (context.getComponent().getPageType()) {
                    case FIRST -> currentPage.gotoFirstItemPage();
                    case LAST -> currentPage.gotoLastItemPage();
                    case NEXT -> currentPage.gotoNextItemPage();
                    case PREVIOUS -> currentPage.gotoPreviousItemPage();
                    case BACK -> {
                        response.gotoPreviousPage();
                        response.gotoItemPage(0);
                    }
                }

                context.getResponseCacheEntry().updateResponse(response, false); // Update Response
            })
            .then();
    }

}
