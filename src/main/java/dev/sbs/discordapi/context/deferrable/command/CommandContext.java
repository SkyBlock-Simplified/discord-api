package dev.sbs.discordapi.context.deferrable.command;

import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.context.TypingContext;
import dev.sbs.discordapi.context.deferrable.DeferrableInteractionContext;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Context for application command interactions, extending {@link DeferrableInteractionContext}
 * and {@link TypingContext} with access to the command identifier and resolved command type.
 *
 * <p>
 * This is the base context for all command interaction types - slash commands, user commands,
 * and message commands.
 *
 * @param <T> the specific {@link ApplicationCommandInteractionEvent} subtype
 * @see SlashCommandContext
 * @see UserCommandContext
 * @see MessageCommandContext
 */
public interface CommandContext<T extends ApplicationCommandInteractionEvent> extends DeferrableInteractionContext<T>, TypingContext<T> {

    /**
     * Returns the snowflake identifier of the invoked application command.
     *
     * @return the command id
     */
    default @NotNull Snowflake getCommandId() {
        return this.getEvent().getCommandId();
    }

    /**
     * Returns the {@link DiscordCommand.Type} derived from the underlying event's command type.
     *
     * @return the resolved command type
     */
    @Override
    default @NotNull DiscordCommand.Type getType() {
        return DiscordCommand.Type.of(this.getEvent().getCommandType().getValue());
    }

}
