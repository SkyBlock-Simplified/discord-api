package dev.sbs.discordapi.listener.message.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.message.interaction.component.selectmenu.SelectMenuContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.action.SelectMenu;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;

public final class SelectMenuListener extends ComponentListener<SelectMenuInteractionEvent, SelectMenuContext, SelectMenu> {

    public SelectMenuListener(DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected SelectMenuContext getContext(SelectMenuInteractionEvent event, Response response, SelectMenu component) {
        return SelectMenuContext.of(this.getDiscordBot(), event, response, component);
    }

    @Override
    protected void handlePaging(SelectMenuContext context) {
        context.getResponse().ifPresent(response -> {
            response.gotoPage(context.getValues().get(0));
            context.getResponseCacheEntry().updateResponse(response, false); // Update Response
        });
    }

}
