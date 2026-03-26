package dev.sbs.discordapi.listener.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.interaction.RadioGroup;
import dev.sbs.discordapi.context.component.RadioGroupContext;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Listener for {@link RadioGroup} component interactions, updating the group's
 * selected value and constructing a {@link RadioGroupContext} for the handler.
 */
public final class RadioGroupListener extends ComponentListener<ComponentInteractionEvent, RadioGroupContext, RadioGroup> {

    /**
     * Constructs a new {@code RadioGroupListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public RadioGroupListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull RadioGroupContext getContext(@NotNull ComponentInteractionEvent event, @NotNull Response response, @NotNull RadioGroup component, @NotNull Optional<Followup> followup) {
        /*String value = event.getInteraction()
            .getCommandInteraction()
            .flatMap(ci -> ci.getValues().map(values -> values.isEmpty() ? null : values.getFirst()))
            .orElse(null);

        if (value != null)
            component.updateSelected(value);
        else
            component.updateSelected();*/

        return RadioGroupContext.of(
            this.getDiscordBot(),
            event,
            response,
            component.updateSelected(
                event.getInteraction()
                    .getCommandInteraction()
                    .flatMap(ci -> ci.getValues().map(values -> values.isEmpty() ? null : values.getFirst()))
                    .orElse(null)
            ),
            followup
        );
    }

}
