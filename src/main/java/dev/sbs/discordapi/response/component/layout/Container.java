package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.builder.annotation.BuildFlag;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.response.component.type.TopLevelMessageComponent;
import dev.sbs.discordapi.response.component.type.v2.ContainerComponent;
import discord4j.discordjson.json.ImmutableComponentData;
import discord4j.discordjson.possible.Possible;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Container implements LayoutComponent, TopLevelMessageComponent {

    private final @NotNull String identifier;
    private final @NotNull Optional<Color> accent;
    private final @NotNull ConcurrentList<ContainerComponent> components;
    private final boolean spoiler;

    public static @NotNull Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Container container = (Container) o;

        return new EqualsBuilder()
            .append(this.getIdentifier(), container.getIdentifier())
            .append(this.getAccent(), container.getAccent())
            .append(this.getComponents(), container.getComponents())
            .append(this.isSpoiler(), container.isSpoiler())
            .build();
    }

    public static @NotNull Builder from(@NotNull Container container) {
        return builder()
            .withIdentifier(container.getIdentifier())
            .withAccent(container.getAccent())
            .withComponents(container.getComponents())
            .isSpoiler(container.isSpoiler());
    }

    @Override
    public @NotNull discord4j.core.object.component.Container getD4jComponent() {
        return Reflection.of(discord4j.core.object.component.Container.class).newInstance(
            ImmutableComponentData.builder()
                .type(this.getType().getValue())
                .id(Possible.absent())
                .components(
                    this.getComponents()
                        .stream()
                        .map(ContainerComponent::getD4jComponent)
                        .map(discord4j.core.object.component.MessageComponent::getData)
                        .collect(Concurrent.toList())
                )
                .spoiler(this.isSpoiler())
                .accentColor(Possible.of(this.getAccent().map(Color::getRGB)))
                .build()
        );
    }

    @Override
    public @NotNull Type getType() {
        return Type.CONTAINER;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getIdentifier())
            .append(this.getAccent())
            .append(this.getComponents())
            .append(this.isSpoiler())
            .build();
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements dev.sbs.api.util.builder.Builder<Container> {

        @BuildFlag(nonNull = true)
        private String identifier;
        private Optional<Color> accent = Optional.empty();
        @BuildFlag(notEmpty = true)
        private final ConcurrentList<ContainerComponent> components = Concurrent.newList();
        private boolean spoiler = false;

        public Builder isSpoiler() {
            return this.isSpoiler(true);
        }

        public Builder isSpoiler(boolean spoiler) {
            this.spoiler = spoiler;
            return this;
        }

        /**
         * Sets the accent color of the {@link Container}.
         *
         * @param accent The accent color to set for the container.
         */
        public Builder withAccent(@Nullable Color accent) {
            return this.withAccent(Optional.ofNullable(accent));
        }

        /**
         * Sets the accent color of the {@link Container}.
         *
         * @param accent The accent color to set for the container.
         */
        public Builder withAccent(@NotNull Optional<Color> accent) {
            this.accent = accent;
            return this;
        }

        /**
         * Add {@link ContainerComponent ContainerComponents} to the {@link Container}.
         *
         * @param components Variable number of section components to add.
         */
        public Builder withComponents(@NotNull ContainerComponent... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Add {@link ContainerComponent ContainerComponents} to the {@link Container}.
         *
         * @param components Collection of section components to add.
         */
        public Builder withComponents(@NotNull Iterable<ContainerComponent> components) {
            components.forEach(this.components::add);
            return this;
        }

        /**
         * Overrides the default identifier of the {@link Container}.
         *
         * @param identifier The identifier to use.
         */
        public Builder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Overrides the default identifier of the {@link Container}.
         *
         * @param identifier The identifier to use.
         * @param args The objects used to format the identifier.
         */
        public Builder withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
            this.identifier = String.format(identifier, args);
            return this;
        }

        @Override
        public @NotNull Container build() {
            Reflection.validateFlags(this);

            return new Container(
                this.identifier,
                this.accent,
                this.components.toUnmodifiableList(),
                this.spoiler
            );
        }

    }

}
