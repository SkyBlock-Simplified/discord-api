package dev.simplified.discordapi.component.interaction;

import dev.simplified.discordapi.component.Component;
import dev.simplified.discordapi.component.capability.Toggleable;
import dev.simplified.discordapi.component.layout.Label;
import dev.simplified.discordapi.component.scope.ActionComponent;
import dev.simplified.discordapi.component.scope.LabelComponent;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import discord4j.core.object.component.CheckboxAction;
import discord4j.discordjson.json.ComponentData;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * An immutable single toggle checkbox component rendered inside a {@link Modal}.
 *
 * <p>
 * A checkbox represents a boolean on/off toggle. It never emits its own interaction event; its
 * checked state is read from the modal submission via {@link #updateFromData(ComponentData)} and
 * exposed through {@link #isSelected()}.
 *
 * <p>
 * Instances are created via {@link #builder()} and can be copied for modification
 * via {@link #mutate()}.
 *
 * @see Label
 * @see Modal
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Checkbox implements ActionComponent, LabelComponent, Toggleable {

    /** The unique identifier for this checkbox. */
    private final @NotNull String identifier;

    /** Whether this checkbox was checked in the submitted modal. */
    private boolean selected;

    /** Whether this checkbox is currently enabled. */
    private boolean enabled;

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

        Checkbox that = (Checkbox) o;

        return this.isSelected() == that.isSelected()
            && this.isEnabled() == that.isEnabled()
            && Objects.equals(this.getIdentifier(), that.getIdentifier());
    }

    /**
     * Creates a pre-filled builder from the given checkbox.
     *
     * @param checkbox the checkbox to copy fields from
     * @return a pre-filled {@link Builder} instance
     */
    public static @NotNull Builder from(@NotNull Checkbox checkbox) {
        return new Builder()
            .withIdentifier(checkbox.getIdentifier())
            .setEnabled(checkbox.isEnabled());
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull discord4j.core.object.component.CheckboxAction getD4jComponent() {
        return CheckboxAction.of(this.getIdentifier());
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Component.Type getType() {
        return Component.Type.CHECKBOX;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getIdentifier(), this.isSelected(), this.isEnabled());
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
        this.selected = data.value().toOptional().map(Boolean::parseBoolean).orElse(false);
    }

    /** {@inheritDoc} */
    @Override
    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    /**
     * A builder for constructing {@link Checkbox} instances.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder {

        @BuildFlag(nonNull = true)
        private String identifier;
        private boolean enabled = true;

        /**
         * Sets the {@link Checkbox} as disabled.
         */
        public Builder setDisabled() {
            return this.setDisabled(true);
        }

        /**
         * Sets whether the {@link Checkbox} is disabled.
         *
         * @param value {@code true} to disable the checkbox
         */
        public Builder setDisabled(boolean value) {
            return this.setEnabled(!value);
        }

        /**
         * Sets the {@link Checkbox} as enabled.
         */
        public Builder setEnabled() {
            return this.setEnabled(true);
        }

        /**
         * Sets whether the {@link Checkbox} is enabled.
         *
         * @param value {@code true} to enable the checkbox
         */
        public Builder setEnabled(boolean value) {
            this.enabled = value;
            return this;
        }

        /**
         * Sets the identifier of the {@link Checkbox}, overriding the default random UUID.
         *
         * @param identifier the identifier to use
         */
        public Builder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Sets the identifier of the {@link Checkbox} using a format string, overriding the default random UUID.
         *
         * @param identifier the format string for the identifier
         * @param args the format arguments
         */
        public Builder withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
            this.identifier = String.format(identifier, args);
            return this;
        }

        /**
         * Builds a new {@link Checkbox} from the configured fields.
         *
         * @return a new {@link Checkbox} instance
         */
        public @NotNull Checkbox build() {
            Reflection.validateFlags(this);

            return new Checkbox(
                this.identifier,
                false,
                this.enabled
            );
        }

    }

}
