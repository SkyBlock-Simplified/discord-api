package dev.sbs.discordapi.context.interaction.deferrable.component.modal;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.interaction.deferrable.component.ComponentContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.Modal;
import dev.sbs.discordapi.util.cache.ResponseCache;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface ModalContext extends ComponentContext {

    @Override
    @NotNull ModalSubmitInteractionEvent getEvent();

    @Override
    @NotNull Modal getComponent();

    static ModalContext of(@NotNull DiscordBot discordBot, @NotNull ModalSubmitInteractionEvent event, @NotNull Response response, @NotNull Modal modal, @NotNull Optional<ResponseCache.Followup> followup) {
        return new ModalContextImpl(
            discordBot,
            event,
            response.getUniqueId(),
            modal.mutate()
                .updateComponents(event)
                .build(),
            followup
        );
    }

}
