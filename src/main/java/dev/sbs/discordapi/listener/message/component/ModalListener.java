package dev.sbs.discordapi.listener.message.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.interaction.deferrable.component.modal.ModalContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.Modal;
import dev.sbs.discordapi.util.cache.ResponseCache;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

public final class ModalListener extends ComponentListener<ModalSubmitInteractionEvent, ModalContext, Modal> {

    public ModalListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected Mono<ModalContext> handleEvent(@NotNull ModalSubmitInteractionEvent event, @NotNull ResponseCache.Entry responseCacheEntry) {
        return Mono.justOrEmpty(responseCacheEntry.getActiveModal()) // Handle Active Modal
            .filter(modal -> event.getCustomId().equals(modal.getIdentifier())) // Validate Message ID
            .doOnNext(modal -> responseCacheEntry.clearModal())
            .flatMap(modal -> this.handleInteraction(event, responseCacheEntry, modal));
    }

    @Override
    protected ModalContext getContext(@NotNull ModalSubmitInteractionEvent event, @NotNull Response response, @NotNull Modal component) {
        return ModalContext.of(this.getDiscordBot(), event, response, component);
    }

    @Override
    protected Mono<Void> handlePaging(@NotNull ModalContext context) {
        return Mono.empty();
    }

}
