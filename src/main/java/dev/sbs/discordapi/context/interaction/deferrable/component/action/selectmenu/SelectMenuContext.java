package dev.sbs.discordapi.context.interaction.deferrable.component.action.selectmenu;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.interaction.deferrable.component.action.ActionComponentContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;

import java.util.function.Function;

public interface SelectMenuContext extends ActionComponentContext {

    @Override
    SelectMenuInteractionEvent getEvent();

    @Override
    SelectMenu getComponent();

    default ConcurrentList<SelectMenu.Option> getSelected() {
        return this.getComponent().getSelected();
    }

    default void modify(Function<SelectMenu.SelectMenuBuilder, SelectMenu.SelectMenuBuilder> selectMenuBuilder) {
        this.modify(selectMenuBuilder.apply(this.getComponent().mutate()).build());
    }

    static SelectMenuContext of(DiscordBot discordBot, SelectMenuInteractionEvent event, Response cachedMessage, SelectMenu selectMenu) {
        return new SelectMenuContextImpl(discordBot, event, cachedMessage.getUniqueId(), selectMenu);
    }

}
