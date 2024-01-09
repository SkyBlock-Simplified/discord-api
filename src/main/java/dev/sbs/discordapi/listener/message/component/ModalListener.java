package dev.sbs.discordapi.listener.message.component;

import dev.sbs.api.util.Range;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.deferrable.component.modal.ModalContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.Modal;
import dev.sbs.discordapi.response.component.interaction.action.TextInput;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;
import dev.sbs.discordapi.response.page.handler.ItemHandler;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
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
            .flatMap(modal -> modal.getPageType() == Modal.PageType.NONE ?
                this.handleInteraction(event, entry, modal, followup) :
                this.handlePagingInteraction(event, entry, modal, followup)
            )
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
        return Flux.fromIterable(context.getComponent().getComponents())
            .map(LayoutComponent::getComponents)
            .map(ConcurrentList::getFirst)
            .flatMap(Mono::justOrEmpty)
            .map(TextInput.class::cast)
            .filter(textInput -> textInput.getValue().isPresent())
            .singleOrEmpty()
            .switchIfEmpty(context.deferEdit().then(Mono.empty())) // Magic Cancel
            .flatMap(textInput -> {
                ItemHandler<?> itemHandler = context.getResponse()
                    .getHistoryHandler()
                    .getCurrentPage()
                    .getItemHandler();

                switch (textInput.getSearchType()) {
                    case PAGE -> {
                        Range<Integer> pageRange = Range.between(1, itemHandler.getTotalItemPages());
                        itemHandler.gotoItemPage(pageRange.fit(Integer.parseInt(textInput.getValue().orElseThrow())));
                    }
                    case INDEX -> {
                        Range<Integer> indexRange = Range.between(0, itemHandler.getCachedFilteredItems().size());
                        int index = indexRange.fit(Integer.parseInt(textInput.getValue().orElseThrow()));
                        itemHandler.gotoItemPage((int) Math.ceil((double) index / itemHandler.getAmountPerPage()));
                    }
                    case CUSTOM -> itemHandler.processSearch(textInput);
                }

                return Mono.empty();
            });
    }

}
