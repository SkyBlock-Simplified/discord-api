package dev.sbs.discordapi.response.component.layout;

import dev.sbs.discordapi.response.component.type.ContainerComponent;
import dev.sbs.discordapi.response.component.type.TopLevelMessageComponent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Separator implements TopLevelMessageComponent, ContainerComponent {

    private final @NotNull Size size;
    private final boolean visible;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Separator separator = (Separator) o;

        return Objects.equals(this.getSize(), separator.getSize())
            && this.isVisible() == separator.isVisible();
    }

    @Override
    public @NotNull Type getType() {
        return Type.SEPARATOR;
    }

    public static @NotNull Separator small() {
        return small(false);
    }

    public static @NotNull Separator small(boolean visible) {
        return new Separator(Size.SMALL, visible);
    }

    public static @NotNull Separator large() {
        return large(false);
    }

    public static @NotNull Separator large(boolean visible) {
        return new Separator(Size.LARGE, visible);
    }

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

    @Getter
    @RequiredArgsConstructor
    public enum Size {

        SMALL(1),
        LARGE(2);

        private final int value;

        public static @NotNull Size of(int value) {
            return switch (value) {
                case 1 -> SMALL;
                case 2 -> LARGE;
                default -> throw new UnsupportedOperationException("Unknown Size: " + value);
            };
        }
    }

}
