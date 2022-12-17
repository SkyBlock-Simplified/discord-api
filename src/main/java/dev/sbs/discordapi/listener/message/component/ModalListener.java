package dev.sbs.discordapi.listener.message.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.interaction.deferrable.component.modal.ModalContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.Modal;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import reactor.core.publisher.Mono;

public final class ModalListener extends ComponentListener<ModalSubmitInteractionEvent, ModalContext, Modal> {

    public ModalListener(DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected ModalContext getContext(ModalSubmitInteractionEvent event, Response response, Modal component) {
        return ModalContext.of(this.getDiscordBot(), event, response, component);
    }

    @Override
    protected Mono<Void> handlePaging(ModalContext context) {
        return Mono.empty();
    }

}
