package dev.sbs.discordapi.listener.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.interaction.Button;
import dev.sbs.discordapi.context.component.ButtonContext;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class ButtonListener extends ComponentListener<ButtonInteractionEvent, ButtonContext, Button> {

    public ButtonListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull ButtonContext getContext(@NotNull ButtonInteractionEvent event, @NotNull Response response, @NotNull Button component, @NotNull Optional<Followup> followup) {
        return ButtonContext.of(
            this.getDiscordBot(),
            event,
            response,
            component,
            followup
        );
    }

}
