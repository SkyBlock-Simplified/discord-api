package dev.sbs.discordapi.context.deferrable.component.action;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public interface ButtonContext extends ActionComponentContext {

    @Override
    @NotNull ButtonInteractionEvent getEvent();

    @Override
    @NotNull Button getComponent();

    default Mono<Void> modify(@NotNull Function<Button.Builder, Button.Builder> buttonBuilder) {
        return this.modify(buttonBuilder.apply(this.getComponent().mutate()).build());
    }

    static @NotNull ButtonContext of(@NotNull DiscordBot discordBot, @NotNull ButtonInteractionEvent event, @NotNull Response response, @NotNull Button button, @NotNull Optional<Followup> followup) {
        return new Impl(
            discordBot,
            event,
            response.getUniqueId(),
            button,
            followup
        );
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements ButtonContext {

        private final @NotNull DiscordBot discordBot;
        private final @NotNull ButtonInteractionEvent event;
        private final @NotNull UUID responseId;
        private final @NotNull Button component;
        private final @NotNull Optional<Followup> followup;

    }

}
