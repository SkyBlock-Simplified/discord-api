package dev.sbs.discordapi.context.interaction.deferrable.component.action.selectmenu;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.interaction.deferrable.component.action.ActionComponentContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.function.Function;

public interface SelectMenuContext extends ActionComponentContext {

    @Override
    SelectMenuInteractionEvent getEvent();

    @Override
    @NotNull SelectMenu getComponent();

    default @NotNull ConcurrentList<SelectMenu.Option> getSelected() {
        return this.getComponent().getSelected();
    }

    default Mono<Void> modify(Function<SelectMenu.Builder, SelectMenu.Builder> selectMenuBuilder) {
        return this.modify(selectMenuBuilder.apply(this.getComponent().mutate()).build());
    }

    static SelectMenuContext of(DiscordBot discordBot, SelectMenuInteractionEvent event, Response cachedMessage, SelectMenu selectMenu) {
        return new SelectMenuContextImpl(discordBot, event, cachedMessage.getUniqueId(), selectMenu);
    }

}
