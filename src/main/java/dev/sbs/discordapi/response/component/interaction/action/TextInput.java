package dev.sbs.discordapi.response.component.interaction.action;

import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.helper.NumberUtil;
import dev.sbs.discordapi.response.component.interaction.Modal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextInput extends ActionComponent {

    @Getter private final @NotNull UUID uniqueId;
    @Getter private final @NotNull Style style;
    @Getter private final @NotNull Optional<String> label;
    @Getter private final @NotNull Optional<String> value;
    @Getter private final @NotNull Optional<String> placeholder;
    @Getter private final int minLength;
    @Getter private final int maxLength;
    @Getter private final boolean required;

    public static TextInputBuilder builder() {
        return new TextInputBuilder(UUID.randomUUID());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        TextInput textInput = (TextInput) o;

        return new EqualsBuilder()
            .append(this.getMinLength(), textInput.getMinLength())
            .append(this.getMaxLength(), textInput.getMaxLength())
            .append(this.isRequired(), textInput.isRequired())
            .append(this.getUniqueId(), textInput.getUniqueId())
            .append(this.getStyle(), textInput.getStyle())
            .append(this.getLabel(), textInput.getLabel())
            .append(this.getValue(), textInput.getValue())
            .append(this.getPlaceholder(), textInput.getPlaceholder())
            .build();
    }

    public static TextInputBuilder from(@NotNull TextInput textInput) {
        return new TextInputBuilder(textInput.getUniqueId())
            .withStyle(textInput.getStyle())
            .withLabel(textInput.getLabel())
            .withValue(textInput.getValue())
            .withPlaceholder(textInput.getPlaceholder())
            .withMinLength(textInput.getMinLength())
            .withMaxLength(textInput.getMaxLength())
            .isRequired(textInput.isRequired());
    }

    @Override
    public discord4j.core.object.component.TextInput getD4jComponent() {
        String label = this.getLabel().orElse(null);

        return (
            switch (this.getStyle()) {
                case PARAGRAPH -> discord4j.core.object.component.TextInput.paragraph(this.getUniqueId().toString(), label, minLength, maxLength);
                case SHORT, UNKNOWN -> discord4j.core.object.component.TextInput.small(this.getUniqueId().toString(), label, minLength, maxLength);
            })
            .prefilled(this.getValue().orElse(""))
            .placeholder(this.getPlaceholder().orElse(""))
            .required(this.isRequired());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(this.getUniqueId())
            .append(this.getStyle())
            .append(this.getLabel())
            .append(this.getValue())
            .append(this.getPlaceholder())
            .append(this.getMinLength())
            .append(this.getMaxLength())
            .append(this.isRequired())
            .build();
    }

    public TextInputBuilder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class TextInputBuilder implements Builder<TextInput> {

        private final UUID uniqueId;
        private Style style = Style.UNKNOWN;
        private Optional<String> label = Optional.empty();
        private Optional<String> value = Optional.empty();
        private Optional<String> placeholder = Optional.empty();
        private int minLength;
        private int maxLength;
        private boolean required;

        /**
         * Sets this {@link TextInput} as required when submitting a {@link Modal}.
         */
        public TextInputBuilder isRequired() {
            return this.isRequired(true);
        }

        /**
         * Sets whether this {@link TextInput} is required when submitting a {@link Modal}.
         *
         * @param required True to require this textinput.
         */
        public TextInputBuilder isRequired(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Sets the label of the {@link TextInput}.
         *
         * @param label The label of the textinput.
         */
        public TextInputBuilder withLabel(@Nullable String label) {
            return this.withLabel(Optional.ofNullable(label));
        }

        /**
         * Sets the label of the {@link TextInput}.
         *
         * @param label The label of the textinput.
         */
        public TextInputBuilder withLabel(@NotNull Optional<String> label) {
            this.label = label;
            return this;
        }

        /**
         * Sets the minimum length required in the {@link TextInput}.
         *
         * @param minLength The minimum length required.
         */
        public TextInputBuilder withMinLength(int minLength) {
            this.minLength = NumberUtil.ensureRange(minLength, 0, 4000);
            return this;
        }

        /**
         * Sets the maximum length required in the {@link TextInput}.
         *
         * @param maxLength The maximum length required.
         */
        public TextInputBuilder withMaxLength(int maxLength) {
            this.maxLength = NumberUtil.ensureRange(maxLength, 1, 4000);
            return this;
        }

        /**
         * Sets the placeholder text of the {@link TextInput}.
         *
         * @param placeholder The placeholder text of the textinput.
         */
        public TextInputBuilder withPlaceholder(@Nullable String placeholder) {
            return this.withPlaceholder(Optional.ofNullable(placeholder));
        }

        /**
         * Sets the placeholder text of the {@link TextInput}.
         *
         * @param placeholder The placeholder text of the textinput.
         */
        public TextInputBuilder withPlaceholder(@NotNull Optional<String> placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        /**
         * Sets the {@link Style} of the {@link TextInput}.
         *
         * @param style The style of the textinput.
         */
        public TextInputBuilder withStyle(@NotNull Style style) {
            this.style = style;
            return this;
        }

        /**
         * Sets the value of the {@link TextInput}.
         *
         * @param value The label of the textinput.
         */
        public TextInputBuilder withValue(@Nullable String value) {
            return this.withValue(Optional.ofNullable(value));
        }

        /**
         * Sets the value of the {@link TextInput}.
         *
         * @param value The label of the textinput.
         */
        public TextInputBuilder withValue(@NotNull Optional<String> value) {
            this.value = value;
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link SelectMenu} component.
         */
        @Override
        public TextInput build() {
            return new TextInput(
                this.uniqueId,
                this.style,
                this.label,
                this.value,
                this.placeholder,
                this.minLength,
                this.maxLength,
                this.required
            );
        }

    }

    @RequiredArgsConstructor
    public enum Style {

        UNKNOWN(-1),
        SHORT(1),
        PARAGRAPH(2);

        /**
         * The Discord TextInput Integer value for this style.
         */
        @Getter private final int value;

        public static Style of(int value) {
            return Arrays.stream(values()).filter(style -> style.getValue() == value).findFirst().orElse(UNKNOWN);
        }

    }

}
