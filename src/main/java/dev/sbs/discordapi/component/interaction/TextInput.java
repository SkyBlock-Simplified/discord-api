package dev.sbs.discordapi.component.interaction;

import dev.sbs.api.math.Range;
import dev.sbs.api.util.NumberUtil;
import dev.sbs.api.util.StringUtil;
import dev.sbs.api.util.builder.BuildFlag;
import dev.sbs.api.util.builder.ClassBuilder;
import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.layout.Label;
import dev.sbs.discordapi.component.type.LabelComponent;
import dev.sbs.discordapi.context.component.ModalContext;
import dev.sbs.discordapi.response.handler.item.ItemHandler;
import discord4j.discordjson.json.ComponentData;
import discord4j.discordjson.possible.Possible;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An immutable text input field component for use within a {@link Modal}.
 * <p>
 * Text inputs accept user-provided text in either a single-line ({@link Style#SHORT})
 * or multi-line ({@link Style#PARAGRAPH}) format. A {@link SearchType} can be assigned
 * to provide built-in search and navigation behavior for {@link ItemHandler}-backed
 * responses. An optional {@link #getValidator() validator} predicate controls whether
 * submitted values are accepted.
 * <p>
 * Instances are created via {@link #builder()} and can be copied for modification
 * via {@link #mutate()}.
 *
 * @see Modal
 * @see SearchType
 * @see Style
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextInput implements ActionComponent, LabelComponent {

    private static final @NotNull Predicate<String> NOOP_HANDLER = __ -> true;

    /** The unique identifier for this text input. */
    private final @NotNull String identifier;

    /** The visual style of this text input. */
    private final @NotNull Style style;

    /** The optional pre-filled value. */
    private final @NotNull Optional<String> value;

    /** The optional placeholder text shown when the input is empty. */
    private final @NotNull Optional<String> placeholder;

    /** The built-in search behavior assigned to this text input. */
    private final @NotNull SearchType searchType;

    /** The validator predicate applied to submitted values. */
    private final @NotNull Predicate<String> validator;

    /** The minimum character length required. */
    private final int minLength;

    /** The maximum character length allowed. */
    private final int maxLength;

    /** Whether this text input must be filled before the modal can be submitted. */
    private final boolean required;

    /**
     * Creates a new builder with a random identifier.
     *
     * @return a new {@link Builder} instance
     */
    public static @NotNull Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        TextInput that = (TextInput) o;

        return Objects.equals(this.getIdentifier(), that.getIdentifier())
            && Objects.equals(this.getStyle(), that.getStyle())
            && Objects.equals(this.getValue(), that.getValue())
            && Objects.equals(this.getPlaceholder(), that.getPlaceholder())
            && Objects.equals(this.getSearchType(), that.getSearchType())
            && Objects.equals(this.getValidator(), that.getValidator())
            && this.getMinLength() == that.getMinLength()
            && this.getMaxLength() == that.getMaxLength()
            && this.isRequired() == that.isRequired();
    }

    /**
     * Creates a pre-filled builder from the given text input.
     *
     * @param textInput the text input to copy fields from
     * @return a pre-filled {@link Builder} instance
     */
    public static @NotNull Builder from(@NotNull TextInput textInput) {
        return new Builder()
            .withIdentifier(textInput.getIdentifier())
            .withStyle(textInput.getStyle())
            .withValue(textInput.getValue())
            .withPlaceholder(textInput.getPlaceholder())
            .withSearchType(textInput.getSearchType())
            .withValidator(textInput.getValidator())
            .withMinLength(textInput.getMinLength())
            .withMaxLength(textInput.getMaxLength())
            .isRequired(textInput.isRequired());
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull discord4j.core.object.component.TextInput getD4jComponent() {
        return (discord4j.core.object.component.TextInput) discord4j.core.object.component.TextInput.fromData(
            ComponentData.builder()
                .type(Component.Type.TEXT_INPUT.getValue())
                .style(this.getStyle().getValue())
                .customId(this.getIdentifier())
                .value(this.getValue().map(Possible::of).orElse(Possible.absent()))
                .placeholder(this.getPlaceholder().map(Possible::of).orElse(Possible.absent()))
                .minLength(this.getMinLength())
                .maxLength(this.getMaxLength())
                .required(this.isRequired())
                .build()
        );
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Type getType() {
        return Type.TEXT_INPUT;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getIdentifier(), this.getStyle(), this.getValue(), this.getPlaceholder(), this.getMinLength(), this.getMaxLength(), this.isRequired());
    }

    /**
     * Creates a pre-filled builder from this instance for modification.
     *
     * @return a pre-filled {@link Builder} instance
     */
    public @NotNull Builder mutate() {
        return from(this);
    }

    /**
     * A builder for constructing {@link TextInput} instances.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder implements ClassBuilder<TextInput> {

        @BuildFlag(nonNull = true, notEmpty = true)
        private String identifier;
        @BuildFlag(nonNull = true)
        private Style style = Style.SHORT;
        //@BuildFlag(nonNull = true, notEmpty = true)
        //private Optional<String> label = Optional.empty();
        @BuildFlag(nonNull = true)
        private Optional<String> value = Optional.empty();
        @BuildFlag(nonNull = true)
        private Optional<String> placeholder = Optional.empty();
        private int minLength = 0;
        private int maxLength = 4000;
        private boolean required;
        @BuildFlag(nonNull = true)
        private TextInput.SearchType searchType = TextInput.SearchType.NONE;
        @BuildFlag(nonNull = true)
        private Optional<Predicate<String>> validator = Optional.empty();

        /**
         * Sets the {@link TextInput} as required when submitting a {@link Modal}.
         */
        public Builder isRequired() {
            return this.isRequired(true);
        }

        /**
         * Sets whether the {@link TextInput} is required when submitting a {@link Modal}.
         *
         * @param required {@code true} to require this text input
         */
        public Builder isRequired(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Sets the identifier of the {@link TextInput}, overriding the default random UUID.
         *
         * @param identifier the identifier to use
         */
        public Builder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Sets the identifier of the {@link TextInput} using a format string, overriding the default random UUID.
         *
         * @param identifier the format string for the identifier
         * @param args the format arguments
         */
        public Builder withIdentifier(@NotNull String identifier, @Nullable Object... args) {
            this.identifier = String.format(identifier, args);
            return this;
        }

        /**
         * Sets the minimum character length required in the {@link TextInput}.
         *
         * @param minLength the minimum length, clamped to the range 0 - 4000
         */
        public Builder withMinLength(int minLength) {
            this.minLength = NumberUtil.ensureRange(minLength, 0, 4000);
            return this;
        }

        /**
         * Sets the maximum character length allowed in the {@link TextInput}.
         *
         * @param maxLength the maximum length, clamped to the range 1 - 4000
         */
        public Builder withMaxLength(int maxLength) {
            this.maxLength = NumberUtil.ensureRange(maxLength, 1, 4000);
            return this;
        }

        /**
         * Sets the placeholder text shown when the {@link TextInput} is empty.
         *
         * @param placeholder the placeholder text, or {@code null} to clear
         */
        public Builder withPlaceholder(@Nullable String placeholder) {
            return this.withPlaceholder(Optional.ofNullable(placeholder));
        }

        /**
         * Sets the placeholder text shown when the {@link TextInput} is empty, using a format string.
         *
         * @param placeholder the format string for the placeholder
         * @param args the format arguments
         */
        public Builder withPlaceholder(@PrintFormat @Nullable String placeholder, @Nullable Object... args) {
            return this.withPlaceholder(StringUtil.formatNullable(placeholder, args));
        }

        /**
         * Sets the placeholder text shown when the {@link TextInput} is empty.
         *
         * @param placeholder the optional placeholder text
         */
        public Builder withPlaceholder(@NotNull Optional<String> placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        /**
         * Sets the {@link SearchType} controlling built-in search behavior.
         *
         * @param searchType the search type to assign
         */
        public Builder withSearchType(@NotNull SearchType searchType) {
            this.searchType = searchType;
            return this;
        }

        /**
         * Sets the visual {@link Style} of the {@link TextInput}.
         *
         * @param style the text input style
         */
        public Builder withStyle(@NotNull Style style) {
            this.style = style;
            return this;
        }

        /**
         * Sets a custom validator predicate for the {@link TextInput}.
         *
         * @param validator the validator predicate, or {@code null} for no validation
         */
        public Builder withValidator(@Nullable Predicate<String> validator) {
            return this.withValidator(Optional.ofNullable(validator));
        }

        /**
         * Sets a custom validator predicate for the {@link TextInput}.
         *
         * @param validator the optional validator predicate
         */
        public Builder withValidator(@NotNull Optional<Predicate<String>> validator) {
            this.validator = validator;
            return this;
        }

        /**
         * Sets the pre-filled value of the {@link TextInput}.
         *
         * @param value the pre-filled text, or {@code null} to clear
         */
        public Builder withValue(@Nullable String value) {
            return this.withValue(Optional.ofNullable(value));
        }

        /**
         * Sets the pre-filled value of the {@link TextInput} using a format string.
         *
         * @param value the format string for the value
         * @param args the format arguments
         */
        public Builder withValue(@PrintFormat @Nullable String value, @Nullable Object... args) {
            return this.withValue(StringUtil.formatNullable(value, args));
        }

        /**
         * Sets the pre-filled value of the {@link TextInput}.
         *
         * @param value the optional pre-filled text
         */
        public Builder withValue(@NotNull Optional<String> value) {
            this.value = value;
            return this;
        }

        /**
         * Builds a new {@link TextInput} from the configured fields.
         *
         * @return a new {@link TextInput} instance
         */
        @Override
        public @NotNull TextInput build() {
            return new TextInput(
                this.identifier,
                this.style,
                this.value,
                this.placeholder,
                this.searchType,
                this.validator.orElse(NOOP_HANDLER),
                this.minLength,
                this.maxLength,
                this.required
            );
        }

    }

    /**
     * Built-in search behaviors for modal text input fields.
     * <p>
     * Each constant provides a title, description, placeholder function, validator function,
     * and an interaction handler that operates on the current page's {@link ItemHandler}.
     */
    @Getter
    @RequiredArgsConstructor
    public enum SearchType {

        /** No-op search type with no navigation behavior. */
        NONE((c_, t_) -> Mono.empty()),
        /** Navigates to a specific page number within the paginated results. */
        PAGE(
            "Go to Page",
            "null",
            itemHandler -> String.format("Enter a number between 1 and %d.", itemHandler.getTotalPages()),
            itemHandler -> value -> {
                if (!NumberUtil.isCreatable(value))
                    return false;

                Range<Integer> pageRange = Range.between(1, itemHandler.getTotalPages());
                int page = NumberUtil.createInteger(value);
                return pageRange.contains(page);
            },
            (context, textInput) -> context.consumeResponse(response -> {
                ItemHandler<?> itemHandler = context.getResponse().getHistoryHandler().getCurrentPage().getItemHandler();
                Range<Integer> pageRange = Range.between(1, itemHandler.getTotalPages());
                itemHandler.gotoPage(pageRange.fit(Integer.parseInt(textInput.getValue().orElseThrow())));
            })
        ),
        /** Navigates to a specific item index within the paginated results. */
        INDEX(
            "Go to Index",
            "null",
            itemHandler -> String.format("Enter a number between 0 and %d.", itemHandler.getCachedFilteredItems().size()),
            itemHandler -> value -> {
                if (!NumberUtil.isCreatable(value))
                    return false;

                Range<Integer> indexRange = Range.between(0, itemHandler.getCachedFilteredItems().size());
                int index = NumberUtil.createInteger(value);
                return indexRange.contains(index);
            },
            (context, textInput) -> context.consumeResponse(response -> {
                ItemHandler<?> itemHandler = context.getResponse().getHistoryHandler().getCurrentPage().getItemHandler();
                Range<Integer> indexRange = Range.between(0, itemHandler.getCachedFilteredItems().size());
                int index = indexRange.fit(Integer.parseInt(textInput.getValue().orElseThrow()));
                itemHandler.gotoPage((int) Math.ceil((double) index / itemHandler.getAmountPerPage()));
            })
        ),
        /** Delegates to the item handler's search handler for custom search logic. */
        CUSTOM((context, textInput) -> context.consumeResponse(response -> context.getResponse()
            .getHistoryHandler()
            .getCurrentPage()
            .getItemHandler()
            .getSearchHandler()
            .search(textInput)
        ));

        /** The display title for this search type. */
        private final @NotNull String title;

        /** The optional description shown alongside the text input. */
        private final @NotNull Optional<String> description;

        /** Generates placeholder text based on the current item handler state. */
        private final @NotNull Function<ItemHandler<?>, String> placeholder;

        /** Generates a validator predicate based on the current item handler state. */
        private final @NotNull Function<ItemHandler<?>, Predicate<String>> validator;

        /** The interaction handler invoked when the modal containing this search type is submitted. */
        private final @NotNull BiFunction<ModalContext, TextInput, Mono<Void>> interaction;

        SearchType(@NotNull BiFunction<ModalContext, TextInput, Mono<Void>> interaction) {
            this(Optional.empty(), f_ -> "", f_ -> p_ -> true, interaction);
        }

        SearchType(
            @NotNull String title,
            @Nullable String description,
            @NotNull Function<ItemHandler<?>, String> placeholder,
            @NotNull Function<ItemHandler<?>, Predicate<String>> validator,
            @NotNull BiFunction<ModalContext, TextInput, Mono<Void>> interaction
        ) {
            this(title, Optional.ofNullable(description), placeholder, validator, interaction);
        }

        /**
         * Builds a {@link Label} wrapping a configured {@link TextInput} for the given item handler.
         *
         * @param itemHandler the item handler used to generate placeholder and validator
         * @return a new {@link Label} containing the configured text input
         */
        public @NotNull Label build(@NotNull ItemHandler<?> itemHandler) {
            return Label.builder()
                .withTitle(this.getTitle())
                .withDescription(this.getDescription())
                .withComponent(
                    TextInput.builder()
                        .withStyle(Style.SHORT)
                        .withSearchType(this)
                        .withPlaceholder(this.getPlaceholder().apply(itemHandler))
                        .withValidator(this.getValidator().apply(itemHandler))
                        .build()
                )
                .build();
        }

    }

    /**
     * Visual style of a {@link TextInput}.
     */
    @Getter
    @RequiredArgsConstructor
    public enum Style {

        /** Fallback for unrecognized style values. */
        UNKNOWN(-1),
        /** Single-line text input. */
        SHORT(1),
        /** Multi-line text area. */
        PARAGRAPH(2);

        /** The Discord integer value for this style. */
        private final int value;

        /**
         * Returns the constant matching the given value, or {@code UNKNOWN} if unrecognized.
         *
         * @param value the Discord integer value
         * @return the matching {@link Style}
         */
        public static @NotNull Style of(int value) {
            return Arrays.stream(values())
                .filter(style -> style.getValue() == value)
                .findFirst()
                .orElse(UNKNOWN);
        }

    }

}
