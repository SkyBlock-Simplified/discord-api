package dev.sbs.discordapi.context.deferrable.component.action;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public interface SelectMenuContext extends ActionComponentContext {

    @Override
    @NotNull SelectMenuInteractionEvent getEvent();

    @Override
    @NotNull SelectMenu getComponent();

    default @NotNull ConcurrentList<SelectMenu.Option> getSelected() {
        return this.getComponent().getSelected();
    }

    default Mono<Void> modify(@NotNull Function<SelectMenu.Builder, SelectMenu.Builder> selectMenuBuilder) {
        return this.modify(selectMenuBuilder.apply(this.getComponent().mutate()).build());
    }

    static SelectMenuContext of(@NotNull DiscordBot discordBot, @NotNull SelectMenuInteractionEvent event, @NotNull Response cachedMessage, SelectMenu selectMenu, @NotNull Optional<Response.Cache.Followup> followup) {
        return new Impl(
            discordBot,
            event,
            cachedMessage.getUniqueId(),
            selectMenu,
            followup
        );
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements SelectMenuContext {

        private final @NotNull DiscordBot discordBot;
        private final @NotNull SelectMenuInteractionEvent event;
        private final @NotNull UUID responseId;
        private final @NotNull SelectMenu component;
        private final @NotNull Optional<Response.Cache.Followup> followup;

    }

}
