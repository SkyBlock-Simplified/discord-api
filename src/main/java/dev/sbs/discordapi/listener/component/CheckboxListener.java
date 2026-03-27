package dev.sbs.discordapi.listener.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.interaction.Checkbox;
import dev.sbs.discordapi.context.component.CheckboxContext;
import dev.sbs.discordapi.handler.response.ResponseFollowup;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Listener for {@link Checkbox} component interactions, constructing a
 * {@link CheckboxContext} and delegating to the checkbox's registered handler.
 */
public final class CheckboxListener extends ComponentListener<ComponentInteractionEvent, CheckboxContext, Checkbox> {

    /**
     * Constructs a new {@code CheckboxListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public CheckboxListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull CheckboxContext getContext(@NotNull ComponentInteractionEvent event, @NotNull Response response, @NotNull Checkbox component, @NotNull Optional<ResponseFollowup> followup) {
        return CheckboxContext.of(
            this.getDiscordBot(),
            event,
            response,
            component,
            followup
        );
    }

}
