package dev.sbs.discordapi.context.message.interaction.component.selectmenu;

import dev.sbs.discordapi.context.message.interaction.component.ComponentContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.action.SelectMenu;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;

import java.util.function.Function;

public interface OptionContext extends ComponentContext {

    @Override
    SelectMenuInteractionEvent getEvent();

    @Override
    SelectMenu getComponent();

    SelectMenu.Option getOption();

    default void modify(Function<SelectMenu.Option.OptionBuilder, SelectMenu.Option.OptionBuilder> optionBuilder) {
        this.modify(
            this.getComponent()
                .mutate()
                .editOption(
                    optionBuilder.apply(this.getOption().mutate()).build()
                )
                .build()
        );
    }

    static OptionContext of(SelectMenuContext selectMenuContext, Response response, SelectMenu.Option option) {
        return new OptionContextImpl(selectMenuContext.getDiscordBot(), selectMenuContext.getEvent(), response.getUniqueId(), selectMenuContext.getComponent(), option);
    }

}
