package dev.simplified.discordapi.component.interaction;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.discordapi.component.Component;
import dev.simplified.discordapi.component.capability.ModalProcessable;
import dev.simplified.discordapi.component.capability.Toggleable;
import dev.simplified.discordapi.component.layout.Label;
import dev.simplified.discordapi.component.scope.ActionComponent;
import dev.simplified.discordapi.component.scope.LabelComponent;
import dev.simplified.discordapi.context.component.ModalContext;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.util.StringUtil;
import discord4j.discordjson.json.ComponentData;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * An immutable multi-selection checkbox group component rendered within a Discord message.
 *
 * <p>
 * Checkbox groups present a list of {@link Option options} from which a user may choose
 * one or more values inside a modal, bounded by {@link #getMinValues()} and
 * {@link #getMaxValues()}. The chosen values are read from the modal submission via
 * {@link #updateFromData(ComponentData)} and exposed through {@link #getSelected()}.
 *
 * <p>
 * Instances are created via {@link #builder()} and can be copied for modification
 * via {@link #mutate()}.
 *
 * @see Option
 * @see Label
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class CheckboxGroup implements ActionComponent, LabelComponent, Toggleable, ModalProcessable {

    /** The unique identifier for this checkbox group. */
    private final @NotNull String identifier;

    /** The available options within this checkbox group. */
    private final @NotNull ConcurrentList<Option> options;

    /** The minimum number of options that must be selected. */
    private final int minValues;

    /** The maximum number of options that may be selected. */
    private final int maxValues;

    /** Whether this checkbox group is required. */
    private final boolean required;

    /** The currently selected options. */
    private @NotNull ConcurrentList<Option> selected;

    /** Whether this checkbox group is currently enabled. */
    private boolean enabled;

    /** The processor invoked with this group on modal submit. */
    @Getter(AccessLevel.NONE)
    private @NotNull Optional<BiFunction<ModalContext, CheckboxGroup, Mono<Void>>> submitProcessor;

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

        CheckboxGroup that = (CheckboxGroup) o;

        return this.getMinValues() == that.getMinValues()
            && this.getMaxValues() == that.getMaxValues()
            && this.isRequired() == that.isRequired()
            && this.isEnabled() == that.isEnabled()
            && Objects.equals(this.getIdentifier(), that.getIdentifier())
            && Objects.equals(this.getOptions(), that.getOptions())
            && Objects.equals(this.getSelected(), that.getSelected());
    }

    /**
     * Finds the first {@link Option} matching the given predicate.
     *
     * @param function the accessor used to extract the comparison value from each option
     * @param value the value to match against
     * @param <S> the comparison type
     * @return the matching option, if present
     */
    public <S> Optional<Option> findOption(@NotNull Function<Option, S> function, S value) {
        return this.getOptions().stream()
            .filter(option -> Objects.equals(function.apply(option), value))
            .findFirst();
    }

    /**
     * Creates a pre-filled builder from the given checkbox group.
     *
     * @param checkboxGroup the checkbox group to copy fields from
     * @return a pre-filled {@link Builder} instance
     */
    public static @NotNull Builder from(@NotNull CheckboxGroup checkboxGroup) {
        return new Builder()
            .withIdentifier(checkboxGroup.getIdentifier())
            .setDisabled(checkboxGroup.isEnabled())
            .withOptions(checkboxGroup.getOptions())
            .withMinValues(checkboxGroup.getMinValues())
            .withMaxValues(checkboxGroup.getMaxValues())
            .setRequired(checkboxGroup.isRequired())
            .withSubmitProcessor(checkboxGroup.submitProcessor);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull discord4j.core.object.component.CheckboxGroupAction getD4jComponent() {
        return discord4j.core.object.component.CheckboxGroupAction.of(
                this.getIdentifier(),
                this.getOptions()
                    .stream()
                    .map(option -> option.getD4jOption(this.getSelected().contains(option)))
                    .collect(Concurrent.toList())
            )
            .withMinValues(this.getMinValues())
            .withMaxValues(this.getMaxValues())
            .required(true)
            .disabled(true);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Component.Type getType() {
        return Component.Type.CHECKBOX_GROUP;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getIdentifier(), this.getOptions(), this.getMinValues(), this.getMaxValues(), this.isRequired(), this.getSelected(), this.isEnabled());
    }

    /**
     * Creates a pre-filled builder from this instance for modification.
     *
     * @return a pre-filled {@link Builder} instance
     */
    public @NotNull Builder mutate() {
        return from(this);
    }

    /** {@inheritDoc} */
    @Override
    public void updateFromData(@NotNull ComponentData data) {
        this.updateSelected(data.values().toOptional().orElse(Concurrent.newList()));
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<Void> processModalSubmit(@NotNull ModalContext context) {
        return this.submitProcessor.map(processor -> processor.apply(context, this)).orElseGet(Mono::empty);
    }

    /** {@inheritDoc} */
    @Override
    public void bindSubmitProcessor(@NotNull Function<ModalContext, Mono<Void>> processor) {
        this.submitProcessor = Optional.of((context, self) -> processor.apply(context));
    }

    /** {@inheritDoc} */
    @Override
    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    /**
     * Updates the selected options to an empty selection.
     *
     * @return this checkbox group
     */
    public @NotNull CheckboxGroup updateSelected() {
        return this.updateSelected(Concurrent.newList());
    }

    /**
     * Updates the selected options by matching the given values against existing options.
     *
     * @param values the option values to select
     * @return this checkbox group
     */
    public @NotNull CheckboxGroup updateSelected(@NotNull String... values) {
        return this.updateSelected(Arrays.asList(values));
    }

    /**
     * Updates the selected options by matching the given values against existing options.
     *
     * @param values the option values to select
     * @return this checkbox group
     */
    public @NotNull CheckboxGroup updateSelected(@NotNull List<String> values) {
        this.selected = values.stream()
            .map(value -> this.findOption(Option::getValue, value))
            .flatMap(Optional::stream)
            .collect(Concurrent.toUnmodifiableList());
        return this;
    }

    /**
     * A builder for constructing {@link CheckboxGroup} instances.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {

        @BuildFlag(nonNull = true)
        private String identifier;
        private boolean enabled;
        @BuildFlag(notEmpty = true)
        private final ConcurrentList<Option> options = Concurrent.newList();
        private int minValues = 0;
        private int maxValues = 1;
        private boolean required;
        private Optional<BiFunction<ModalContext, CheckboxGroup, Mono<Void>>> submitProcessor = Optional.empty();

        /**
         * Sets the processor invoked with this group when the enclosing {@link Modal} is submitted.
         *
         * @param processor the submit processor, receiving the modal context and this group
         */
        public Builder onSubmit(@NotNull BiFunction<ModalContext, CheckboxGroup, Mono<Void>> processor) {
            this.submitProcessor = Optional.of(processor);
            return this;
        }

        private Builder withSubmitProcessor(@NotNull Optional<BiFunction<ModalContext, CheckboxGroup, Mono<Void>>> submitProcessor) {
            this.submitProcessor = submitProcessor;
            return this;
        }

        /**
         * Sets the {@link CheckboxGroup} as disabled.
         */
        public Builder setDisabled() {
            return this.setDisabled(true);
        }

        /**
         * Sets whether the {@link CheckboxGroup} is disabled.
         *
         * @param value {@code true} to disable the checkbox group
         */
        public Builder setDisabled(boolean value) {
            return this.setEnabled(!value);
        }

        /**
         * Sets the {@link CheckboxGroup} as enabled.
         */
        public Builder setEnabled() {
            return this.setEnabled(true);
        }

        /**
         * Sets whether the {@link CheckboxGroup} is enabled.
         *
         * @param value {@code true} to enable the checkbox group
         */
        public Builder setEnabled(boolean value) {
            this.enabled = value;
            return this;
        }

        /**
         * Sets the {@link CheckboxGroup} as required.
         */
        public Builder setRequired() {
            return this.setRequired(true);
        }

        /**
         * Sets whether the {@link CheckboxGroup} is required.
         *
         * @param value {@code true} to mark the checkbox group as required
         */
        public Builder setRequired(boolean value) {
            this.required = value;
            return this;
        }

        /**
         * Sets the identifier of the {@link CheckboxGroup}, overriding the default random UUID.
         *
         * @param identifier the identifier to use
         */
        public Builder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Sets the identifier of the {@link CheckboxGroup} using a format string, overriding the default random UUID.
         *
         * @param identifier the format string for the identifier
         * @param args the format arguments
         */
        public Builder withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
            this.identifier = String.format(identifier, args);
            return this;
        }

        /**
         * Sets the minimum number of options that must be selected.
         *
         * @param minValues the minimum selection count
         */
        public Builder withMinValues(int minValues) {
            this.minValues = minValues;
            return this;
        }

        /**
         * Sets the maximum number of options that may be selected.
         *
         * @param maxValues the maximum selection count
         */
        public Builder withMaxValues(int maxValues) {
            this.maxValues = maxValues;
            return this;
        }

        /**
         * Adds {@link Option options} to the {@link CheckboxGroup}.
         *
         * @param options variable number of options to add
         */
        public Builder withOptions(@NotNull Option... options) {
            return this.withOptions(Arrays.asList(options));
        }

        /**
         * Adds {@link Option options} to the {@link CheckboxGroup}.
         *
         * @param options collection of options to add
         */
        public Builder withOptions(@NotNull Iterable<Option> options) {
            options.forEach(this.options::add);
            return this;
        }

        /**
         * Builds a new {@link CheckboxGroup} from the configured fields.
         *
         * @return a new {@link CheckboxGroup} instance
         */
        public @NotNull CheckboxGroup build() {
            Reflection.validateFlags(this);

            return new CheckboxGroup(
                this.identifier,
                this.options,
                this.minValues,
                this.maxValues,
                this.required,
                Concurrent.newUnmodifiableList(),
                this.enabled,
                this.submitProcessor
            );
        }

    }

    /**
     * An individual option within a {@link CheckboxGroup}.
     * <p>
     * Each option has a display label, a submission value, and may include an optional
     * description.
     */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Option {

        /** The internal unique identifier for this option. */
        private final @NotNull UUID uniqueId;

        /** The display label shown to the user. */
        private final @NotNull String label;

        /** The value submitted when this option is selected. */
        private final @NotNull String value;

        /** The optional description shown beneath the label. */
        private final @NotNull Optional<String> description;

        /**
         * Creates a new builder with a random unique identifier.
         *
         * @return a new {@link Builder} instance
         */
        public static @NotNull Builder builder() {
            return new Builder(UUID.randomUUID());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Option option = (Option) o;

            return Objects.equals(this.getUniqueId(), option.getUniqueId())
                && Objects.equals(this.getLabel(), option.getLabel())
                && Objects.equals(this.getValue(), option.getValue())
                && Objects.equals(this.getDescription(), option.getDescription());
        }

        /**
         * Creates a pre-filled builder from the given option.
         *
         * @param option the option to copy fields from
         * @return a pre-filled {@link Builder} instance
         */
        public static @NotNull Builder from(@NotNull Option option) {
            return new Builder(option.getUniqueId())
                .withLabel(option.getLabel())
                .withValue(option.getValue())
                .withDescription(option.getDescription());
        }

        /**
         * Converts this option to its Discord4J representation.
         *
         * @param selected whether this option should be marked as the default selection
         * @return the Discord4J option
         */
        public @NotNull discord4j.core.object.component.CheckboxGroupAction.Option getD4jOption(boolean selected) {
            discord4j.core.object.component.CheckboxGroupAction.Option d4jOption = discord4j.core.object.component.CheckboxGroupAction.Option.of(this.getLabel(), this.getValue())
                .withDefault(selected);

            if (this.getDescription().isPresent())
                d4jOption = d4jOption.withDescription(this.getDescription().get());

            return d4jOption;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getUniqueId(), this.getLabel(), this.getValue(), this.getDescription());
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
         * A builder for constructing {@link Option} instances.
         */
        @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
        public static final class Builder {

            private final UUID uniqueId;
            private Optional<String> label = Optional.empty();
            private Optional<String> value = Optional.empty();
            private Optional<String> description = Optional.empty();

            /**
             * Sets the description displayed beneath the {@link Option} label.
             *
             * @param description the description text, or {@code null} to clear
             */
            public Builder withDescription(@Nullable String description) {
                return this.withDescription(Optional.ofNullable(description));
            }

            /**
             * Sets the description displayed beneath the {@link Option} label using a format string.
             *
             * @param description the format string for the description
             * @param args the format arguments
             */
            public Builder withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
                return this.withDescription(StringUtil.formatNullable(description, args));
            }

            /**
             * Sets the description displayed beneath the {@link Option} label.
             *
             * @param description the optional description text
             */
            public Builder withDescription(@NotNull Optional<String> description) {
                this.description = description;
                return this;
            }

            /**
             * Sets the display label of the {@link Option}.
             *
             * @param label the label text
             */
            public Builder withLabel(@NotNull String label) {
                this.label = Optional.of(label);
                return this;
            }

            /**
             * Sets the display label of the {@link Option} using a format string.
             *
             * @param label the format string for the label
             * @param args the format arguments
             */
            public Builder withLabel(@PrintFormat @NotNull String label, @Nullable Object... args) {
                this.label = Optional.of(String.format(label, args));
                return this;
            }

            /**
             * Sets the submission value of the {@link Option}.
             *
             * @param value the option value
             */
            public Builder withValue(@NotNull String value) {
                this.value = Optional.of(value);
                return this;
            }

            /**
             * Sets the submission value of the {@link Option} using a format string.
             *
             * @param value the format string for the value
             * @param args the format arguments
             */
            public Builder withValue(@PrintFormat @NotNull String value, @Nullable Object... args) {
                this.value = Optional.of(String.format(value, args));
                return this;
            }

            /**
             * Builds a new {@link Option} from the configured fields.
             *
             * @return a new {@link Option} instance
             */
            public @NotNull Option build() {
                return new Option(
                    this.uniqueId,
                    this.label.orElse(this.uniqueId.toString()),
                    this.value.orElse(this.uniqueId.toString()),
                    this.description
                );
            }

        }

    }

}
