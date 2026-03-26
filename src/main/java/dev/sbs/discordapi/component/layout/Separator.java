package dev.sbs.discordapi.component.layout;

import dev.sbs.discordapi.component.type.ContainerComponent;
import dev.sbs.discordapi.component.type.TopLevelMessageComponent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * An immutable visual divider placed between components in a message or container.
 *
 * <p>
 * Separators are created via the {@link #small()} or {@link #large()} factory methods,
 * with an optional visibility flag controlling whether the divider line is rendered.
 *
 * @see Container
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Separator implements TopLevelMessageComponent, ContainerComponent {

    /** The spacing size of this separator. */
    private final @NotNull Size size;

    /** Whether the divider line is visually rendered. */
    private final boolean visible;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Separator separator = (Separator) o;

        return Objects.equals(this.getSize(), separator.getSize())
            && this.isVisible() == separator.isVisible();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Type getType() {
        return Type.SEPARATOR;
    }

    /**
     * Creates a small separator with no visible divider line.
     *
     * @return a new small separator
     */
    public static @NotNull Separator small() {
        return small(false);
    }

    /**
     * Creates a small separator with the specified visibility.
     *
     * @param visible {@code true} to render the divider line
     * @return a new small separator
     */
    public static @NotNull Separator small(boolean visible) {
        return new Separator(Size.SMALL, visible);
    }

    /**
     * Creates a large separator with no visible divider line.
     *
     * @return a new large separator
     */
    public static @NotNull Separator large() {
        return large(false);
    }

    /**
     * Creates a large separator with the specified visibility.
     *
     * @param visible {@code true} to render the divider line
     * @return a new large separator
     */
    public static @NotNull Separator large(boolean visible) {
        return new Separator(Size.LARGE, visible);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull discord4j.core.object.component.Separator getD4jComponent() {
        return discord4j.core.object.component.Separator.of(
            this.isVisible(),
            discord4j.core.object.component.Separator.SpacingSize.of(this.getSize().getValue())
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getSize(), this.isVisible());
    }

    /** The spacing size of a {@link Separator}. */
    @Getter
    @RequiredArgsConstructor
    public enum Size {

        /** Small spacing. */
        SMALL(1),

        /** Large spacing. */
        LARGE(2);

        /** The Discord integer value for this size. */
        private final int value;

        /**
         * Resolves a {@link Size} from its Discord integer value.
         *
         * @param value the integer value to resolve
         * @return the corresponding size
         * @throws UnsupportedOperationException if the value does not match a known size
         */
        public static @NotNull Size of(int value) {
            return switch (value) {
                case 1 -> SMALL;
                case 2 -> LARGE;
                default -> throw new UnsupportedOperationException("Unknown Size: " + value);
            };
        }
    }

}
