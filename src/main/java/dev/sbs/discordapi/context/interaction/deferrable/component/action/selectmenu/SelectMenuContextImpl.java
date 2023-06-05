package dev.sbs.discordapi.context.interaction.deferrable.component.action.selectmenu;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.util.cache.ResponseCache;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
class SelectMenuContextImpl implements SelectMenuContext {

    @Getter private final @NotNull DiscordBot discordBot;
    @Getter private final @NotNull SelectMenuInteractionEvent event;
    @Getter private final @NotNull UUID responseId;
    @Getter private final @NotNull SelectMenu component;
    @Getter private final @NotNull Optional<ResponseCache.Followup> followup;

}