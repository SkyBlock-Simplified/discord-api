package dev.sbs.discordapi.command.exception.input;

import dev.sbs.discordapi.util.exception.DiscordUserException;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * {@link InputException InputExceptions} are thrown when provided data is missing or invalid.
 */
@Getter
public class InputException extends DiscordUserException {

    private final @NotNull Optional<String> invalidInput;

    public InputException(@Nullable String invalidInput) {
        this(Optional.ofNullable(invalidInput));
    }

    public InputException(@Nullable String invalidInput, @NotNull String message) {
        super(message);
        this.invalidInput = Optional.ofNullable(invalidInput);
    }

    public InputException(@Nullable String invalidInput, @NotNull String message, @Nullable Object... args) {
        super(String.format(message, args));
        this.invalidInput = Optional.ofNullable(invalidInput);
    }

    public InputException(@NotNull Optional<String> invalidInput) {
        this(invalidInput, "The input you provided does not match the expected input!");
    }

    public InputException(@NotNull Optional<String> invalidInput, @NotNull String message) {
        super(message);
        this.invalidInput = invalidInput;
    }

    public InputException(@NotNull Optional<String> invalidInput, @NotNull String message, @Nullable Object... args) {
        super(String.format(message, args));
        this.invalidInput = invalidInput;
    }

}
