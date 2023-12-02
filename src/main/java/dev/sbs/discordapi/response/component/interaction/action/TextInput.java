package dev.sbs.discordapi.response.component.interaction.action;

import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.api.util.helper.NumberUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.response.component.interaction.Modal;
import discord4j.core.object.component.MessageComponent;
import discord4j.discordjson.json.ComponentData;
import discord4j.discordjson.possible.Possible;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextInput extends ActionComponent {

    private final @NotNull String identifier;
    private final @NotNull Style style;
    private final @NotNull Optional<String> label;
    private final @NotNull Optional<String> value;
    private final @NotNull Optional<String> placeholder;
    private final int minLength;
    private final int maxLength;
    private final boolean required;

    public static @NotNull Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        TextInput textInput = (TextInput) o;

        return new EqualsBuilder()
            .append(this.getIdentifier(), textInput.getIdentifier())
            .append(this.getMinLength(), textInput.getMinLength())
            .append(this.getMaxLength(), textInput.getMaxLength())
            .append(this.isRequired(), textInput.isRequired())
            .append(this.getStyle(), textInput.getStyle())
            .append(this.getLabel(), textInput.getLabel())
            .append(this.getValue(), textInput.getValue())
            .append(this.getPlaceholder(), textInput.getPlaceholder())
            .build();
    }

    public static @NotNull Builder from(@NotNull TextInput textInput) {
        return new Builder()
            .withIdentifier(textInput.getIdentifier())
            .withStyle(textInput.getStyle())
            .withLabel(textInput.getLabel())
            .withValue(textInput.getValue())
            .withPlaceholder(textInput.getPlaceholder())
            .withMinLength(textInput.getMinLength())
            .withMaxLength(textInput.getMaxLength())
            .isRequired(textInput.isRequired());
    }

    @Override
    public @NotNull discord4j.core.object.component.TextInput getD4jComponent() {
        return (discord4j.core.object.component.TextInput) discord4j.core.object.component.TextInput.fromData(
            ComponentData.builder()
                .type(MessageComponent.Type.TEXT_INPUT.getValue())
                .style(this.getStyle().getValue())
                .customId(this.getIdentifier())
                .label(this.getLabel().map(Possible::of).orElse(Possible.absent()))
                .value(this.getValue().map(Possible::of).orElse(Possible.absent()))
                .placeholder(this.getPlaceholder().map(Possible::of).orElse(Possible.absent()))
                .minLength(this.getMinLength())
                .maxLength(this.getMaxLength())
                .required(false)
                .build()
        );
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getIdentifier())
            .append(this.getStyle())
            .append(this.getLabel())
            .append(this.getValue())
            .append(this.getPlaceholder())
            .append(this.getMinLength())
            .append(this.getMaxLength())
            .append(this.isRequired())
            .build();
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder implements dev.sbs.api.util.builder.Builder<TextInput> {

        private String identifier;
        private Style style = Style.UNKNOWN;
        private Optional<String> label = Optional.empty();
        private Optional<String> value = Optional.empty();
        private Optional<String> placeholder = Optional.empty();
        private int minLength = 0;
        private int maxLength = 4000;
        private boolean required;

        /**
         * Sets this {@link TextInput} as required when submitting a {@link Modal}.
         */
        public Builder isRequired() {
            return this.isRequired(true);
        }

        /**
         * Sets whether this {@link TextInput} is required when submitting a {@link Modal}.
         *
         * @param required True to require this textinput.
         */
        public Builder isRequired(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Overrides the default identifier of the {@link TextInput}.
         *
         * @param identifier The identifier to use.
         */
        public Builder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Overrides the default identifier of the {@link TextInput}.
         *
         * @param identifier The identifier to use.
         * @param args Objects used to format the identifier.
         */
        public Builder withIdentifier(@NotNull String identifier, @Nullable Object... args) {
            this.identifier = String.format(identifier, args);
            return this;
        }

        /**
         * Sets the label text of the {@link TextInput}.
         *
         * @param label The label of the field item.
         */
        public Builder withLabel(@Nullable String label) {
            return this.withLabel(Optional.ofNullable(label));
        }

        /**
         * Sets the label text of the {@link TextInput}.
         *
         * @param label The label of the field item.
         * @param objects The objects used to format the label.
         */
        public Builder withLabel(@PrintFormat @Nullable String label, @Nullable Object... objects) {
            return this.withLabel(StringUtil.formatNullable(label, objects));
        }

        /**
         * Sets the label of the {@link TextInput}.
         *
         * @param label The label of the textinput.
         */
        public Builder withLabel(@NotNull Optional<String> label) {
            this.label = label;
            return this;
        }

        /**
         * Sets the minimum length required in the {@link TextInput}.
         *
         * @param minLength The minimum length required.
         */
        public Builder withMinLength(int minLength) {
            this.minLength = NumberUtil.ensureRange(minLength, 0, 4000);
            return this;
        }

        /**
         * Sets the maximum length required in the {@link TextInput}.
         *
         * @param maxLength The maximum length required.
         */
        public Builder withMaxLength(int maxLength) {
            this.maxLength = NumberUtil.ensureRange(maxLength, 1, 4000);
            return this;
        }

        /**
         * Sets the placeholder text of the {@link TextInput}.
         *
         * @param placeholder The placeholder text of the textinput.
         */
        public Builder withPlaceholder(@Nullable String placeholder) {
            return this.withPlaceholder(Optional.ofNullable(placeholder));
        }

        /**
         * Sets the placeholder text of the {@link TextInput}.
         *
         * @param placeholder The placeholder text of the textinput.
         * @param args The objects used to format the placeholder.
         */
        public Builder withPlaceholder(@PrintFormat @Nullable String placeholder, @Nullable Object... args) {
            return this.withPlaceholder(StringUtil.formatNullable(placeholder, args));
        }

        /**
         * Sets the placeholder text of the {@link TextInput}.
         *
         * @param placeholder The placeholder text of the textinput.
         */
        public Builder withPlaceholder(@NotNull Optional<String> placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        /**
         * Sets the {@link Style} of the {@link TextInput}.
         *
         * @param style The style of the textinput.
         */
        public Builder withStyle(@NotNull Style style) {
            this.style = style;
            return this;
        }

        /**
         * Sets the value of the {@link TextInput}.
         *
         * @param value The label of the textinput.
         */
        public Builder withValue(@Nullable String value) {
            return this.withValue(Optional.ofNullable(value));
        }

        /**
         * Sets the value of the {@link TextInput}.
         *
         * @param value The label of the textinput.
         * @param args The objects used to format the value.
         */
        public Builder withValue(@PrintFormat @Nullable String value, @Nullable Object... args) {
            return this.withValue(StringUtil.formatNullable(value, args));
        }

        /**
         * Sets the value of the {@link TextInput}.
         *
         * @param value The label of the textinput.
         */
        public Builder withValue(@NotNull Optional<String> value) {
            this.value = value;
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link SelectMenu} component.
         */
        @Override
        public @NotNull TextInput build() {
            return new TextInput(
                this.identifier,
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

    @Getter
    @RequiredArgsConstructor
    public enum Style {

        UNKNOWN(-1),
        SHORT(1),
        PARAGRAPH(2);

        /**
         * The Discord TextInput Integer value for this style.
         */
        private final int value;

        public static Style of(int value) {
            return Arrays.stream(values()).filter(style -> style.getValue() == value).findFirst().orElse(UNKNOWN);
        }

    }

}
