package dev.sbs.discordapi.context.interaction.deferrable.component.modal;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.response.component.interaction.Modal;
import dev.sbs.discordapi.util.cache.ResponseCache;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
class ModalContextImpl implements ModalContext {

    @Getter private final @NotNull DiscordBot discordBot;
    @Getter private final @NotNull ModalSubmitInteractionEvent event;
    @Getter private final @NotNull UUID responseId;
    @Getter private final @NotNull Modal component;
    @Getter private final @NotNull Optional<ResponseCache.Followup> followup;

}