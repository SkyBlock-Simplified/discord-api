package dev.sbs.discordapi.context.command;

import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.context.DeferrableInteractionContext;
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

    /** The snowflake identifier of the invoked application command. */
    default @NotNull Snowflake getCommandId() {
        return this.getEvent().getCommandId();
    }

    /** {@inheritDoc} */
    @Override
    default @NotNull DiscordCommand.Type getType() {
        return DiscordCommand.Type.of(this.getEvent().getCommandType().getValue());
    }

}
