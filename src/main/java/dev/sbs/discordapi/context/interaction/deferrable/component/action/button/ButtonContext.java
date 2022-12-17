package dev.sbs.discordapi.context.interaction.deferrable.component.action.button;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.interaction.deferrable.component.action.ActionComponentContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.Modal;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.function.Function;

public interface ButtonContext extends ActionComponentContext {

    @Override
    ButtonInteractionEvent getEvent();

    @Override
    Button getComponent();

    default void modify(Function<Button.ButtonBuilder, Button.ButtonBuilder> buttonBuilder) {
        this.modify(buttonBuilder.apply(this.getComponent().mutate()).build());
    }

    default Mono<Void> presentModal(@NotNull Modal modal) {
        // TODO: Cache modal associated to response
        return this.getEvent().presentModal(modal.getD4jPresentSpec());
    }

    static ButtonContext of(DiscordBot discordBot, ButtonInteractionEvent event, Response response, Button button) {
        return new ButtonContextImpl(discordBot, event, response.getUniqueId(), button);
    }

}
