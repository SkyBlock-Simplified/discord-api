package dev.sbs.discordapi.command.exception.input;

import lombok.Getter;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * {@link ExpectedInputException ExpectedInputExceptions} are thrown when the provided data does not match expected data.
 */
@Getter
public class ExpectedInputException extends InputException {

    private final @NotNull Optional<String> expectedInput;

    public ExpectedInputException(@Nullable String invalidInput) {
        this(Optional.ofNullable(invalidInput));
    }

    public ExpectedInputException(@NotNull Optional<String> invalidInput) {
        super(invalidInput);
        this.expectedInput = Optional.empty();
    }

    public ExpectedInputException(@Nullable String invalidInput, @Nullable String expectedInput) {
        this(Optional.ofNullable(invalidInput), Optional.ofNullable(expectedInput));
    }

    public ExpectedInputException(@NotNull Optional<String> invalidInput, @NotNull Optional<String> expectedInput) {
        super(invalidInput, "The input you provided does not match the expected input.");
        this.expectedInput = expectedInput;
    }

    public ExpectedInputException(@Nullable String invalidInput, @Nullable String expectedInput, @NotNull String message) {
        super(invalidInput, message);
        this.expectedInput = Optional.ofNullable(expectedInput);
    }

    public ExpectedInputException(@Nullable String invalidInput, @Nullable String expectedInput, @NotNull @PrintFormat String message, @Nullable Object... args) {
        this(Optional.ofNullable(invalidInput), Optional.ofNullable(expectedInput), message, args);
    }

    public ExpectedInputException(@NotNull Optional<String> invalidInput, @NotNull Optional<String> expectedInput, @NotNull @PrintFormat String message, @Nullable Object... args) {
        super(invalidInput, String.format(message, args));
        this.expectedInput = expectedInput;
    }

}
