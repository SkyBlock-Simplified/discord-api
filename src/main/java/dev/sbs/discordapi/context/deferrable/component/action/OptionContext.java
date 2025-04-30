package dev.sbs.discordapi.context.deferrable.component.action;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.handler.response.Followup;
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

public interface OptionContext extends ActionComponentContext {

    @Override
    @NotNull SelectMenuInteractionEvent getEvent();

    @Override
    @NotNull SelectMenu getComponent();

    @NotNull SelectMenu.Option getOption();

    default Mono<Void> modify(@NotNull Function<SelectMenu.Option.Builder, SelectMenu.Option.Builder> optionBuilder) {
        return this.modify(
            this.getComponent()
                .mutate()
                .editOption(
                    optionBuilder.apply(this.getOption().mutate()).build()
                )
                .build()
        );
    }

    static @NotNull OptionContext of(@NotNull SelectMenuContext selectMenuContext, @NotNull Response response, @NotNull SelectMenu.Option option) {
        return new Impl(
            selectMenuContext.getDiscordBot(),
            selectMenuContext.getEvent(),
            response.getUniqueId(),
            selectMenuContext.getComponent(),
            option,
            selectMenuContext.getFollowup()
        );
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements OptionContext {

        private final @NotNull DiscordBot discordBot;
        private final @NotNull SelectMenuInteractionEvent event;
        private final @NotNull UUID responseId;
        private final @NotNull SelectMenu component;
        private final @NotNull SelectMenu.Option option;
        private final @NotNull Optional<Followup> followup;

    }

}
