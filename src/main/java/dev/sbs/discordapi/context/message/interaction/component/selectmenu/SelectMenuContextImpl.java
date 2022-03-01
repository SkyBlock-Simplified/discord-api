package dev.sbs.discordapi.context.message.interaction.component.selectmenu;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.response.component.action.SelectMenu;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
class SelectMenuContextImpl implements SelectMenuContext {

    @Getter private final DiscordBot discordBot;
    @Getter private final SelectMenuInteractionEvent event;
    @Getter private final UUID uniqueId = UUID.randomUUID();
    @Getter private final UUID responseId;
    @Getter private final SelectMenu component;

}