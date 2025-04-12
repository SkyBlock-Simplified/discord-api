package dev.sbs.discordapi.command.exception.input;

import dev.sbs.discordapi.command.parameter.Parameter;
import lombok.Getter;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * {@link ParameterException ParameterExceptions} are thrown when the user passes invalid arguments to a command.
 */
@Getter
public class ParameterException extends InputException {

    private final @NotNull Parameter parameter;
    private final @NotNull Optional<String> value;

    public ParameterException(@NotNull Parameter parameter, @Nullable String value, @NotNull String message) {
        super(message);
        this.parameter = parameter;
        this.value = Optional.ofNullable(value);
    }

    public ParameterException(@NotNull Parameter parameter, @Nullable String value, @NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
        this.parameter = parameter;
        this.value = Optional.ofNullable(value);
    }

}
