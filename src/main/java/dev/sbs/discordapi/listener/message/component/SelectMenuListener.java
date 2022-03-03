package dev.sbs.discordapi.listener.message.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.message.interaction.component.selectmenu.SelectMenuContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.action.SelectMenu;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import reactor.core.publisher.Mono;

public final class SelectMenuListener extends ComponentListener<SelectMenuInteractionEvent, SelectMenuContext, SelectMenu> {

    public SelectMenuListener(DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected SelectMenuContext getContext(SelectMenuInteractionEvent event, Response response, SelectMenu component) {
        return SelectMenuContext.of(this.getDiscordBot(), event, response, component);
    }

    @Override
    protected Mono<Void> handlePaging(SelectMenuContext selectMenuContext) {
        return Mono.just(selectMenuContext)
            .flatMap(context -> Mono.justOrEmpty(context.getResponse())
                .doOnNext(response -> {
                    switch (context.getComponent().getPageType()) {
                        case PAGE -> response.gotoPage(context.getValues().get(0));
                        case SUBPAGE -> response.gotoSubPage(context.getValues().get(0));
                    }

                    context.getResponseCacheEntry().updateResponse(response, false); // Update Response
                })
                .then()
            );
    }

}
