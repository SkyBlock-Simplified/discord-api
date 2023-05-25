package dev.sbs.discordapi.context.interaction.deferrable.component.action.selectmenu;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
class OptionContextImpl implements OptionContext {

    @Getter private final DiscordBot discordBot;
    @Getter private final SelectMenuInteractionEvent event;
    @Getter private final UUID responseId;
    @Getter private final SelectMenu component;
    @Getter private final SelectMenu.Option option;

}
