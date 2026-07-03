package dev.simplified.discordapi.harness.gateway;

import dev.simplified.discordapi.command.parameter.Parameter;
import org.jetbrains.annotations.NotNull;

/**
 * A single resolved slash command option - a leaf {@code name = value} pair carried by a simulated
 * chat-input interaction.
 *
 * <p>
 * The {@link Parameter.Type} tags the value with the same Discord option-type integer the real client
 * sends, so the dispatch matches what the framework's argument resolution expects. Options nest below a
 * top-level command, a subcommand, or a subcommand within a group; the {@link DispatchFactory} places
 * them at the correct depth.
 *
 * @param name the option name, matched against a {@link Parameter#getName() command parameter name}
 * @param type the option type, supplying the Discord option-type integer
 * @param value the raw option value as the gateway delivers it
 */
public record SlashOption(@NotNull String name, @NotNull Parameter.Type type, @NotNull String value) {

    /**
     * Creates a {@link Parameter.Type#TEXT text} option.
     *
     * @param name the option name
     * @param value the raw text value
     * @return the text option
     */
    public static @NotNull SlashOption text(@NotNull String name, @NotNull String value) {
        return new SlashOption(name, Parameter.Type.TEXT, value);
    }

}
