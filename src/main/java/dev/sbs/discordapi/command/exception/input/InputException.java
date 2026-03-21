package dev.sbs.discordapi.command.exception.input;

import dev.sbs.discordapi.exception.DiscordUserException;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Thrown when provided data is missing or invalid.
 */
@Getter
public class InputException extends DiscordUserException {

    /**
     * The invalid input value that caused this exception, if available.
     */
    private final @NotNull Optional<String> invalidInput;

    /**
     * Constructs a new {@code InputException} with the given invalid input and a default message.
     *
     * @param invalidInput the invalid input value, or null if not available
     */
    public InputException(@Nullable String invalidInput) {
        this(Optional.ofNullable(invalidInput));
    }

    /**
     * Constructs a new {@code InputException} with the given invalid input and message.
     *
     * @param invalidInput the invalid input value, or null if not available
     * @param message the detail message
     */
    public InputException(@Nullable String invalidInput, @NotNull String message) {
        super(message);
        this.invalidInput = Optional.ofNullable(invalidInput);
    }

    /**
     * Constructs a new {@code InputException} with the given invalid input and a formatted message.
     *
     * @param invalidInput the invalid input value, or null if not available
     * @param message the format string
     * @param args the format arguments
     */
    public InputException(@Nullable String invalidInput, @NotNull String message, @Nullable Object... args) {
        super(String.format(message, args));
        this.invalidInput = Optional.ofNullable(invalidInput);
    }

    /**
     * Constructs a new {@code InputException} with the given optional invalid input and a default message.
     *
     * @param invalidInput the optional invalid input value
     */
    public InputException(@NotNull Optional<String> invalidInput) {
        this(invalidInput, "The input you provided does not match the expected input!");
    }

    /**
     * Constructs a new {@code InputException} with the given optional invalid input and message.
     *
     * @param invalidInput the optional invalid input value
     * @param message the detail message
     */
    public InputException(@NotNull Optional<String> invalidInput, @NotNull String message) {
        super(message);
        this.invalidInput = invalidInput;
    }

    /**
     * Constructs a new {@code InputException} with the given optional invalid input and a formatted message.
     *
     * @param invalidInput the optional invalid input value
     * @param message the format string
     * @param args the format arguments
     */
    public InputException(@NotNull Optional<String> invalidInput, @NotNull String message, @Nullable Object... args) {
        super(String.format(message, args));
        this.invalidInput = invalidInput;
    }

}
