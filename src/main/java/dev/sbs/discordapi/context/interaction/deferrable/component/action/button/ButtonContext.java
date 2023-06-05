package dev.sbs.discordapi.context.interaction.deferrable.component.action.button;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.interaction.deferrable.component.action.ActionComponentContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.util.cache.ResponseCache;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Function;

public interface ButtonContext extends ActionComponentContext {

    @Override
    @NotNull ButtonInteractionEvent getEvent();

    @Override
    @NotNull Button getComponent();

    default Mono<Void> modify(Function<Button.ButtonBuilder, Button.ButtonBuilder> buttonBuilder) {
        return this.modify(buttonBuilder.apply(this.getComponent().mutate()).build());
    }

    static ButtonContext of(@NotNull DiscordBot discordBot, @NotNull ButtonInteractionEvent event, @NotNull Response response, @NotNull Button button, @NotNull Optional<ResponseCache.Followup> followup) {
        return new ButtonContextImpl(
            discordBot,
            event,
            response.getUniqueId(),
            button,
            followup
        );
    }

}
