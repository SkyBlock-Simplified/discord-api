package dev.sbs.discordapi.context.interaction.deferrable.component.action.selectmenu;

import dev.sbs.discordapi.context.interaction.deferrable.component.action.ActionComponentContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.function.Function;

public interface OptionContext extends ActionComponentContext {

    @Override
    SelectMenuInteractionEvent getEvent();

    @Override
    @NotNull SelectMenu getComponent();

    @NotNull SelectMenu.Option getOption();

    default Mono<Void> modify(Function<SelectMenu.Option.Builder, SelectMenu.Option.Builder> optionBuilder) {
        return this.modify(
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
