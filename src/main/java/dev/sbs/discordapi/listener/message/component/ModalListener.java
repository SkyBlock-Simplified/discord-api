package dev.sbs.discordapi.listener.message.component;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.deferrable.component.modal.ModalContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.Modal;
import dev.sbs.discordapi.response.component.interaction.action.TextInput;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;
import dev.sbs.discordapi.response.page.Page;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;

public final class ModalListener extends ComponentListener<ModalSubmitInteractionEvent, ModalContext, Modal> {

    public ModalListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected Mono<Void> handleEvent(@NotNull ModalSubmitInteractionEvent event, @NotNull Response.Cache.Entry entry, @NotNull Optional<Response.Cache.Followup> followup) {
        return Mono.justOrEmpty(entry.getUserModal(event.getInteraction().getUser())) // Handle User Modal
            .filter(modal -> event.getCustomId().equals(modal.getIdentifier())) // Validate Message ID
            .doOnNext(modal -> entry.clearModal(event.getInteraction().getUser()))
            .flatMap(modal -> switch (modal.getPageType()) {
                case NONE -> this.handleInteraction(event, entry, modal, followup);
                case SEARCH -> this.handlePagingInteraction(event, entry, modal, followup);
            })
            .then(entry.updateLastInteract())
            .then();
    }

    @Override
    protected @NotNull ModalContext getContext(@NotNull ModalSubmitInteractionEvent event, @NotNull Response response, @NotNull Modal component, @NotNull Optional<Response.Cache.Followup> followup) {
        return ModalContext.of(
            this.getDiscordBot(),
            event,
            response,
            component,
            followup
        );
    }

    @Override
    protected Mono<Void> handlePaging(@NotNull ModalContext context) {
        return Mono.justOrEmpty(context.getFollowup())
            .map(Response.Cache.Followup::getResponse)
            .switchIfEmpty(Mono.justOrEmpty(context.getResponse()))
            .flatMap(response -> {
                Page currentPage = response.getHistoryHandler().getCurrentPage();

                switch (context.getComponent().getPageType()) {
                    case SEARCH -> context.getComponent()
                        .getComponents()
                        .stream()
                        .map(LayoutComponent::getComponents)
                        .map(ConcurrentList::getFirst)
                        .flatMap(Optional::stream)
                        .map(TextInput.class::cast)
                        .filter(textInput -> textInput.getValue().isPresent())
                        .findFirst()
                        .ifPresent(textInput -> currentPage.getItemHandler().processSearch(textInput));
                }

                return context.getResponseCacheEntry().updateResponse(response);
            })
            .then();
    }

}
