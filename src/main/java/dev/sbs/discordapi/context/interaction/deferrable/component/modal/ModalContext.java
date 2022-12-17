package dev.sbs.discordapi.context.interaction.deferrable.component.modal;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.interaction.deferrable.component.ComponentContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.Modal;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;

public interface ModalContext extends ComponentContext {

    @Override
    ModalSubmitInteractionEvent getEvent();

    static ModalContext of(DiscordBot discordBot, ModalSubmitInteractionEvent event, Response response, Modal modal) {
        return new ModalContextImpl(discordBot, event, response.getUniqueId(), modal);
    }

}
