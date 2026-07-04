package dev.simplified.discordapi.command;

import dev.simplified.discordapi.util.DiscordReference;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * Stable, serializable identity of a {@link DiscordCommand}.
 *
 * <p>
 * Neither a command's {@link Structure#name() name} nor its Discord-assigned numeric id identifies it
 * uniquely: the same name may exist across {@link DiscordCommand.Type command types} and across
 * {@link Structure#parent() parent}/{@link Structure#group() group} hierarchies, and every subcommand beneath
 * a shared parent collapses onto a single command id. This tuple of type, guild, parent, group, and name is
 * the stable key, mirroring the fields compared by {@link DiscordReference#matchesInteractionData}.
 *
 * @param type the command type - slash, user, or message
 * @param guildId the guild the command is scoped to, or {@code -1} for a global command
 * @param parent the parent subcommand name, or empty when top-level
 * @param group the subcommand group name, or empty when ungrouped
 * @param name the command or leaf-subcommand name
 */
public record CommandKey(
    @NotNull DiscordCommand.Type type,
    long guildId,
    @NotNull String parent,
    @NotNull String group,
    @NotNull String name
) implements Serializable {

    /**
     * Derives the stable identity of a loaded command from its {@link Structure}.
     *
     * @param command the command to key
     * @return the command's identity
     */
    public static @NotNull CommandKey of(@NotNull DiscordCommand<?> command) {
        Structure structure = command.getStructure();
        return new CommandKey(
            command.getType(),
            structure.guildId(),
            structure.parent().name(),
            structure.group().name(),
            structure.name()
        );
    }

}
