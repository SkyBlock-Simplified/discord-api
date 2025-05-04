package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.builder.annotation.BuildFlag;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.response.component.type.TopLevelMessageComponent;
import dev.sbs.discordapi.response.component.type.v2.AccessoryComponent;
import dev.sbs.discordapi.response.component.type.v2.ContainerComponent;
import dev.sbs.discordapi.response.component.type.v2.SectionComponent;
import discord4j.core.object.component.IAccessoryComponent;
import discord4j.core.object.component.ICanBeUsedInSectionComponent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Section implements LayoutComponent, TopLevelMessageComponent, ContainerComponent {

    private final @NotNull String identifier;
    private final @NotNull AccessoryComponent accessory;
    private final @NotNull ConcurrentList<SectionComponent> components;

    public static @NotNull Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Section section = (Section) o;

        return new EqualsBuilder()
            .append(this.getIdentifier(), section.getIdentifier())
            .append(this.getAccessory(), section.getAccessory())
            .append(this.getComponents(), section.getComponents())
            .build();
    }

    public static @NotNull Builder from(@NotNull Section section) {
        return builder()
            .withIdentifier(section.getIdentifier())
            .withAccessory(section.getAccessory())
            .withComponents(section.getComponents());
    }

    @Override
    public @NotNull discord4j.core.object.component.Section getD4jComponent() {
        return discord4j.core.object.component.Section.of(
            (IAccessoryComponent) this.getAccessory().getD4jComponent(),
            this.getComponents()
                .stream()
                .map(SectionComponent::getD4jComponent)
                .map(ICanBeUsedInSectionComponent.class::cast)
                .collect(Concurrent.toList())
        );
    }

    @Override
    public @NotNull Component.Type getType() {
        return Component.Type.SECTION;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getIdentifier())
            .append(this.getAccessory())
            .append(this.getComponents())
            .build();
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements dev.sbs.api.util.builder.Builder<Section> {

        @BuildFlag(nonNull = true)
        private String identifier;
        @BuildFlag(notEmpty = true)
        private Optional<AccessoryComponent> accessory = Optional.empty();
        private final ConcurrentList<SectionComponent> components = Concurrent.newList();

        /**
         * Sets the {@link AccessoryComponent} for the {@link Section}.
         * <ul>
         *     <li>This must be provided</li>
         * </ul>
         *
         * @param accessory The accessory to set for the section.
         */
        public Builder withAccessory(@NotNull AccessoryComponent accessory) {
            this.accessory = Optional.of(accessory);
            return this;
        }

        /**
         * Add {@link SectionComponent SectionComponents} to the {@link Section}.
         *
         * @param components Variable number of section components to add.
         */
        public Builder withComponents(@NotNull SectionComponent... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Add {@link SectionComponent SectionComponents} to the {@link Section}.
         *
         * @param components Collection of section components to add.
         */
        public Builder withComponents(@NotNull Iterable<SectionComponent> components) {
            components.forEach(this.components::add);
            return this;
        }

        /**
         * Overrides the default identifier of the {@link Button}.
         *
         * @param identifier The identifier to use.
         */
        public Builder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Overrides the default identifier of the {@link Button}.
         *
         * @param identifier The identifier to use.
         * @param args The objects used to format the identifier.
         */
        public Builder withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
            this.identifier = String.format(identifier, args);
            return this;
        }

        @Override
        public @NotNull Section build() {
            Reflection.validateFlags(this);

            return new Section(
                this.identifier,
                this.accessory.orElseThrow(),
                this.components.toUnmodifiableList()
            );
        }

    }

}
