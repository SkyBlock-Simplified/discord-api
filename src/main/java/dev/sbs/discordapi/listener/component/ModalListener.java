package dev.sbs.discordapi.listener.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.interaction.Modal;
import dev.sbs.discordapi.context.component.ModalContext;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;

public final class ModalListener extends ComponentListener<ModalSubmitInteractionEvent, ModalContext, Modal> {

    public ModalListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull ModalContext getContext(@NotNull ModalSubmitInteractionEvent event, @NotNull Response response, @NotNull Modal component, @NotNull Optional<Followup> followup) {
        return ModalContext.of(
            this.getDiscordBot(),
            event,
            response,
            component,
            followup
        );
    }

    @Override
    protected Mono<Void> handleEvent(@NotNull ModalSubmitInteractionEvent event, @NotNull CachedResponse entry, @NotNull Optional<Followup> followup) {
        return Mono.justOrEmpty(entry.getUserModal(event.getInteraction().getUser())) // Handle User Modal
            .filter(modal -> event.getCustomId().equals(modal.getIdentifier())) // Validate Message ID
            .doOnNext(modal -> entry.clearModal(event.getInteraction().getUser()))
            .flatMap(modal -> this.handleInteraction(event, entry, modal, followup))
            .then(entry.updateLastInteract())
            .then();
    }

}
