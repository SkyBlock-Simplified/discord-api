package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
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
    @Getter(AccessLevel.NONE)
    private final boolean divider;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Separator separator = (Separator) o;

        return new EqualsBuilder()
            .append(this.getSize(), separator.getSize())
            .append(this.hasDivider(), separator.hasDivider())
            .build();
    }

    @Override
    public @NotNull Type getType() {
        return Type.SEPARATOR;
    }

    public boolean hasDivider() {
        return this.divider;
    }

    public static @NotNull Separator small() {
        return small(false);
    }

    public static @NotNull Separator small(boolean divider) {
        return new Separator(Size.SMALL, divider);
    }

    public static @NotNull Separator large() {
        return large(false);
    }

    public static @NotNull Separator large(boolean divider) {
        return new Separator(Size.LARGE, divider);
    }

    @Override
    public @NotNull discord4j.core.object.component.Separator getD4jComponent() {
        return discord4j.core.object.component.Separator.of(
            this.hasDivider(),
            discord4j.core.object.component.Separator.SpacingSize.of(this.getSize().getValue())
        );
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getSize())
            .append(this.hasDivider())
            .build();
    }

    @Getter
    @RequiredArgsConstructor
    public enum Size {

        SMALL(0),
        LARGE(1);

        private final int value;

        public static @NotNull Size of(int value) {
            return switch (value) {
                case 0 -> SMALL;
                case 1 -> LARGE;
                default -> throw new UnsupportedOperationException("Unknown Size: " + value);
            };
        }
    }

}
