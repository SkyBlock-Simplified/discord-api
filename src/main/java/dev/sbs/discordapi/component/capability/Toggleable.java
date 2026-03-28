package dev.sbs.discordapi.component.capability;

import dev.sbs.discordapi.component.Component;

/**
 * Capability interface for components that can be enabled or disabled.
 *
 * <p>
 * A disabled component is rendered in a non-interactive state and does not emit
 * interaction events when clicked. This interface provides both query methods
 * ({@link #isEnabled()}, {@link #isDisabled()}) and mutation methods
 * ({@link #setEnabled()}, {@link #setEnabled(boolean)}).
 */
public interface Toggleable extends Component {

    /** Whether this component is currently enabled. */
    boolean isEnabled();

    /**
     * Returns {@code true} if this component is currently disabled.
     *
     * <p>
     * This is a convenience inverse of {@link #isEnabled()}.
     *
     * @return {@code true} if disabled, {@code false} if enabled
     */
    default boolean isDisabled() {
        return !this.isEnabled();
    }

    /**
     * Enables this component.
     *
     * <p>
     * Equivalent to calling {@link #setEnabled(boolean) setEnabled(true)}.
     */
    default void setEnabled() {
        this.setEnabled(true);
    }

    /**
     * Sets whether this component is enabled or disabled.
     *
     * @param value {@code true} to enable, {@code false} to disable
     */
    void setEnabled(boolean value);

}
