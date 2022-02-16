package dev.sbs.discordapi.context.message.interaction.component.button;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.message.interaction.component.ComponentContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.action.Button;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;

import java.util.function.Function;

public interface ButtonContext extends ComponentContext {

    @Override
    ButtonInteractionEvent getEvent();

    @Override
    Button getComponent();

    default void modify(Function<Button.ButtonBuilder, Button.ButtonBuilder> buttonBuilder) {
        this.modify(buttonBuilder.apply(this.getComponent().mutate()).build());
    }

    static ButtonContext of(DiscordBot discordBot, ButtonInteractionEvent event, Response response, Button button) {
        return new ButtonContextImpl(discordBot, event, response.getUniqueId(), button);
    }

}
