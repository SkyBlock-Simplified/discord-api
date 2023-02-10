package dev.sbs.discordapi.listener.message.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.interaction.deferrable.component.action.selectmenu.SelectMenuContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
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
            .doOnNext(context -> context.getComponent().updateSelected(context.getEvent().getValues()))
            .flatMap(context -> Mono.justOrEmpty(context.getResponse())
                .doOnNext(response -> {
                    String selectedValue = context.getSelected().getFirst().orElseThrow().getValue();

                    switch (context.getComponent().getPageType()) {
                        case PAGE -> response.gotoPage(selectedValue);
                        case SUBPAGE -> {
                            if (selectedValue.equals("BACK"))
                                response.gotoPreviousPage();
                            else
                                response.gotoSubPage(selectedValue);
                        }
                    }

                    context.getResponseCacheEntry().updateResponse(response);
                })
                .then()
            );
    }

}
