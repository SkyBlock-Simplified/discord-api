package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.type.TopLevelComponent;
import dev.sbs.discordapi.response.component.type.v2.ContainerComponent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Separator implements LayoutComponent, TopLevelComponent, ContainerComponent {

    private final @NotNull ConcurrentList<Component> components = Concurrent.newUnmodifiableList();
    private final @NotNull Size size;
    private final boolean visible;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Separator separator = (Separator) o;

        return new EqualsBuilder()
            .append(this.getSize(), separator.getSize())
            .append(this.isVisible(), separator.isVisible())
            .build();
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
        return new HashCodeBuilder()
            .append(this.getSize())
            .append(this.isVisible())
            .build();
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
