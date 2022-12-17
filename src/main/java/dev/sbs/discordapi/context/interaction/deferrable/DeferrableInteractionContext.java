package dev.sbs.discordapi.context.interaction.deferrable;

import dev.sbs.discordapi.context.interaction.InteractionContext;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;

public interface DeferrableInteractionContext<T extends DeferrableInteractionEvent> extends InteractionContext<T> {

}
