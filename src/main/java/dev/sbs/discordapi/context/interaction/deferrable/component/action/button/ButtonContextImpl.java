package dev.sbs.discordapi.context.interaction.deferrable.component.action.button;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.util.cache.ResponseCache;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
class ButtonContextImpl implements ButtonContext {

    @Getter private final @NotNull DiscordBot discordBot;
    @Getter private final @NotNull ButtonInteractionEvent event;
    @Getter private final @NotNull UUID responseId;
    @Getter private final @NotNull Button component;
    @Getter private final @NotNull Optional<ResponseCache.Followup> followup;

}