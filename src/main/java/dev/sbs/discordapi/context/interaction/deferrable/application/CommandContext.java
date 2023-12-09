package dev.sbs.discordapi.context.interaction.deferrable.application;

import dev.sbs.discordapi.command.reference.CommandReference;
import dev.sbs.discordapi.context.interaction.TypingContext;
import dev.sbs.discordapi.context.interaction.deferrable.DeferrableInteractionContext;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;

public interface CommandContext<T extends ApplicationCommandInteractionEvent> extends DeferrableInteractionContext<T>, TypingContext<T> {

    default @NotNull Snowflake getCommandId() {
        return this.getEvent().getCommandId();
    }

    @Override
    default @NotNull CommandReference.Type getType() {
        return CommandReference.Type.of(this.getEvent().getCommandType().getValue());
    }

}
