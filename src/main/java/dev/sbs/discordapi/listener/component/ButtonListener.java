package dev.sbs.discordapi.listener.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.interaction.Button;
import dev.sbs.discordapi.context.component.ButtonContext;
import dev.sbs.discordapi.handler.response.ResponseFollowup;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Listener for {@link Button} component interactions, constructing a
 * {@link ButtonContext} and delegating to the button's registered handler.
 */
public final class ButtonListener extends ComponentListener<ButtonInteractionEvent, ButtonContext, Button> {

    /**
     * Constructs a new {@code ButtonListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public ButtonListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull ButtonContext getContext(@NotNull ButtonInteractionEvent event, @NotNull Response response, @NotNull Button component, @NotNull Optional<ResponseFollowup> followup) {
        return ButtonContext.of(
            this.getDiscordBot(),
            event,
            response,
            component,
            followup
        );
    }

}
