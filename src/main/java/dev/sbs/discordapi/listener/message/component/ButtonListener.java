package dev.sbs.discordapi.listener.message.component;

import dev.sbs.api.util.Range;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.NumberUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.deferrable.component.action.ButtonContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.Modal;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.response.component.interaction.action.TextInput;
import dev.sbs.discordapi.response.component.layout.ActionRow;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.handler.search.Search;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;

public final class ButtonListener extends ComponentListener<ButtonInteractionEvent, ButtonContext, Button> {

    public ButtonListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull ButtonContext getContext(@NotNull ButtonInteractionEvent event, @NotNull Response response, @NotNull Button component, @NotNull Optional<Response.Cache.Followup> followup) {
        return ButtonContext.of(
            this.getDiscordBot(),
            event,
            response,
            component,
            followup
        );
    }

    @Override
    protected Mono<Void> handlePaging(@NotNull ButtonContext context) {
        return Mono.justOrEmpty(context.getFollowup())
            .map(Response.Cache.Followup::getResponse)
            .switchIfEmpty(Mono.justOrEmpty(context.getResponse()))
            .flatMap(response -> {
                Page currentPage = response.getHistoryHandler().getCurrentPage();

                switch (context.getComponent().getPageType()) {
                    case FIRST -> currentPage.getItemHandler().gotoFirstItemPage();
                    case PREVIOUS -> currentPage.getItemHandler().gotoPreviousItemPage();
                    case NEXT -> currentPage.getItemHandler().gotoNextItemPage();
                    case LAST -> currentPage.getItemHandler().gotoLastItemPage();
                    case BACK -> currentPage.getHistoryHandler().gotoPreviousPage();
                    case SEARCH -> {
                        ConcurrentList<LayoutComponent<ActionComponent>> components = Concurrent.newList(
                            ActionRow.of(
                                TextInput.builder()
                                    .withLabel("Go to Page")
                                    .withPlaceholder("Enter a number between 1 and %d.", currentPage.getItemHandler().getTotalItemPages())
                                    .withSearchType(TextInput.SearchType.PAGE)
                                    .withValidator(value -> {
                                        if (!NumberUtil.isCreatable(value))
                                            return false;

                                        Range<Integer> pageRange = Range.between(1, currentPage.getItemHandler().getTotalItemPages());
                                        int page = NumberUtil.createInteger(value);
                                        return pageRange.contains(page);
                                    })
                                    .build()
                            ),
                            ActionRow.of(
                                TextInput.builder()
                                    .withLabel("Go to Index")
                                    .withPlaceholder("Enter a number between 0 and %d.", currentPage.getItemHandler().getCachedFilteredItems().size())
                                    .withSearchType(TextInput.SearchType.INDEX)
                                    .withValidator(value -> {
                                        if (!NumberUtil.isCreatable(value))
                                            return false;

                                        Range<Integer> indexRange = Range.between(0, currentPage.getItemHandler().getCachedFilteredItems().size());
                                        int index = NumberUtil.createInteger(value);
                                        return indexRange.contains(index);
                                    })
                                    .build()
                            )
                        );

                        components.addAll(currentPage.getItemHandler()
                            .getSearchers()
                            .stream()
                            .map(Search::getTextInput)
                            .map(ActionRow::of)
                            .collect(Concurrent.toList()));

                        return context.presentModal(
                            Modal.builder()
                                .withComponents(components)
                                .withTitle("Search")
                                .withPageType(Modal.PageType.SEARCH)
                                .build()
                        );
                    }
                    case SORT -> currentPage.getItemHandler().getSortHandler().gotoNext();
                    case ORDER -> currentPage.getItemHandler().getSortHandler().invertOrder();
                }

                return context.deferEdit();
            })
            .then();
    }

}
