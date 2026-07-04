package dev.simplified.discordapi.harness.gateway;

import discord4j.core.object.command.ApplicationCommandOption;
import org.jetbrains.annotations.NotNull;

/**
 * A single resolved slash command option - a leaf {@code name = value} pair carried by a simulated
 * chat-input interaction.
 *
 * <p>
 * The {@link ApplicationCommandOption.Type} tags the value with the same Discord option-type integer the real
 * client sends, so the dispatch matches what an interaction handler expects. Options nest below a top-level
 * command, a subcommand, or a subcommand within a group; the {@link DispatchFactory} places them at the
 * correct depth.
 *
 * @param name the option name, matched against the command option of the same name
 * @param type the option type, supplying the Discord option-type integer
 * @param value the raw option value as the gateway delivers it
 */
public record SlashOption(@NotNull String name, @NotNull ApplicationCommandOption.Type type, @NotNull String value) {

    /**
     * Creates a {@link ApplicationCommandOption.Type#STRING string} option.
     *
     * @param name the option name
     * @param value the raw text value
     * @return the text option
     */
    public static @NotNull SlashOption text(@NotNull String name, @NotNull String value) {
        return new SlashOption(name, ApplicationCommandOption.Type.STRING, value);
    }

}
