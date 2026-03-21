package dev.sbs.discordapi.command.exception;

import lombok.Getter;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Thrown when the provided data does not match the expected data.
 */
@Getter
public class ExpectedInputException extends InputException {

    /**
     * The expected input value, if available.
     */
    private final @NotNull Optional<String> expectedInput;

    /**
     * Constructs a new {@code ExpectedInputException} with the given invalid input and a default message.
     *
     * @param invalidInput the invalid input value, or null if not available
     */
    public ExpectedInputException(@Nullable String invalidInput) {
        this(Optional.ofNullable(invalidInput));
    }

    /**
     * Constructs a new {@code ExpectedInputException} with the given optional invalid input and a default message.
     *
     * @param invalidInput the optional invalid input value
     */
    public ExpectedInputException(@NotNull Optional<String> invalidInput) {
        super(invalidInput);
        this.expectedInput = Optional.empty();
    }

    /**
     * Constructs a new {@code ExpectedInputException} with the given invalid and expected input values
     * and a default message.
     *
     * @param invalidInput the invalid input value, or null if not available
     * @param expectedInput the expected input value, or null if not available
     */
    public ExpectedInputException(@Nullable String invalidInput, @Nullable String expectedInput) {
        this(Optional.ofNullable(invalidInput), Optional.ofNullable(expectedInput));
    }

    /**
     * Constructs a new {@code ExpectedInputException} with the given optional invalid and expected input
     * values and a default message.
     *
     * @param invalidInput the optional invalid input value
     * @param expectedInput the optional expected input value
     */
    public ExpectedInputException(@NotNull Optional<String> invalidInput, @NotNull Optional<String> expectedInput) {
        super(invalidInput, "The input you provided does not match the expected input.");
        this.expectedInput = expectedInput;
    }

    /**
     * Constructs a new {@code ExpectedInputException} with the given invalid input, expected input,
     * and message.
     *
     * @param invalidInput the invalid input value, or null if not available
     * @param expectedInput the expected input value, or null if not available
     * @param message the detail message
     */
    public ExpectedInputException(@Nullable String invalidInput, @Nullable String expectedInput, @NotNull String message) {
        super(invalidInput, message);
        this.expectedInput = Optional.ofNullable(expectedInput);
    }

    /**
     * Constructs a new {@code ExpectedInputException} with the given invalid input, expected input,
     * and a formatted message.
     *
     * @param invalidInput the invalid input value, or null if not available
     * @param expectedInput the expected input value, or null if not available
     * @param message the format string
     * @param args the format arguments
     */
    public ExpectedInputException(@Nullable String invalidInput, @Nullable String expectedInput, @NotNull @PrintFormat String message, @Nullable Object... args) {
        this(Optional.ofNullable(invalidInput), Optional.ofNullable(expectedInput), message, args);
    }

    /**
     * Constructs a new {@code ExpectedInputException} with the given optional invalid input,
     * optional expected input, and a formatted message.
     *
     * @param invalidInput the optional invalid input value
     * @param expectedInput the optional expected input value
     * @param message the format string
     * @param args the format arguments
     */
    public ExpectedInputException(@NotNull Optional<String> invalidInput, @NotNull Optional<String> expectedInput, @NotNull @PrintFormat String message, @Nullable Object... args) {
        super(invalidInput, String.format(message, args));
        this.expectedInput = expectedInput;
    }

}
