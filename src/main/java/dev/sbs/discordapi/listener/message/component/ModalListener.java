package dev.sbs.discordapi.listener.message.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.deferrable.component.modal.ModalContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.Modal;
import dev.sbs.discordapi.util.cache.ResponseCache;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;

public final class ModalListener extends ComponentListener<ModalSubmitInteractionEvent, ModalContext, Modal> {

    public ModalListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected Mono<Void> handleEvent(@NotNull ModalSubmitInteractionEvent event, @NotNull ResponseCache.Entry entry, @NotNull Optional<ResponseCache.Followup> followup) {
        return Mono.justOrEmpty(entry.getUserModal(event.getInteraction().getUser())) // Handle User Modal
            .filter(modal -> event.getCustomId().equals(modal.getIdentifier())) // Validate Message ID
            .doOnNext(modal -> entry.clearModal(event.getInteraction().getUser()))
            .flatMap(modal -> this.handleInteraction(event, entry, modal, followup))
            .then(entry.updateLastInteract())
            .then();
    }

    @Override
    protected ModalContext getContext(@NotNull ModalSubmitInteractionEvent event, @NotNull Response response, @NotNull Modal component, @NotNull Optional<ResponseCache.Followup> followup) {
        return ModalContext.of(this.getDiscordBot(), event, response, component, followup);
    }

    @Override
    protected Mono<Void> handlePaging(@NotNull ModalContext context) {
        return Mono.empty();
    }

}
