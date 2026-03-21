package dev.sbs.discordapi.command.exception.input;

import dev.sbs.discordapi.command.parameter.Parameter;
import lombok.Getter;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Thrown when the user passes invalid arguments to a command.
 */
@Getter
public class ParameterException extends InputException {

    /**
     * The command parameter that received invalid input.
     */
    private final @NotNull Parameter parameter;

    /**
     * The invalid value that was provided for the parameter, if available.
     */
    private final @NotNull Optional<String> value;

    /**
     * Constructs a new {@code ParameterException} with the given parameter, value, and message.
     *
     * @param parameter the command parameter that received invalid input
     * @param value the invalid value provided, or null if not available
     * @param message the detail message
     */
    public ParameterException(@NotNull Parameter parameter, @Nullable String value, @NotNull String message) {
        super(message);
        this.parameter = parameter;
        this.value = Optional.ofNullable(value);
    }

    /**
     * Constructs a new {@code ParameterException} with the given parameter, value, and a formatted message.
     *
     * @param parameter the command parameter that received invalid input
     * @param value the invalid value provided, or null if not available
     * @param message the format string
     * @param args the format arguments
     */
    public ParameterException(@NotNull Parameter parameter, @Nullable String value, @NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
        this.parameter = parameter;
        this.value = Optional.ofNullable(value);
    }

}
