package dev.sbs.discordapi.listener.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.interaction.SelectMenu;
import dev.sbs.discordapi.context.component.SelectMenuContext;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class SelectMenuListener extends ComponentListener<SelectMenuInteractionEvent, SelectMenuContext, SelectMenu> {

    public SelectMenuListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull SelectMenuContext getContext(@NotNull SelectMenuInteractionEvent event, @NotNull Response response, @NotNull SelectMenu component, @NotNull Optional<Followup> followup) {
        return SelectMenuContext.of(
            this.getDiscordBot(),
            event,
            response,
            component.updateSelected(event.getValues()),
            followup
        );
    }

}
