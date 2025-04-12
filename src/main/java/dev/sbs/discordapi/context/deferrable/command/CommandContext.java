package dev.sbs.discordapi.context.deferrable.command;

import dev.sbs.discordapi.command.context.TypeContext;
import dev.sbs.discordapi.context.TypingContext;
import dev.sbs.discordapi.context.deferrable.DeferrableInteractionContext;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;

public interface CommandContext<T extends ApplicationCommandInteractionEvent> extends DeferrableInteractionContext<T>, TypingContext<T> {

    default @NotNull Snowflake getCommandId() {
        return this.getEvent().getCommandId();
    }

    @Override
    default @NotNull TypeContext getType() {
        return TypeContext.of(this.getEvent().getCommandType().getValue());
    }

}
