package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.builder.ClassBuilder;
import dev.sbs.api.builder.annotation.BuildFlag;
import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.type.TopLevelComponent;
import dev.sbs.discordapi.response.component.type.v2.AccessoryComponent;
import dev.sbs.discordapi.response.component.type.v2.ContainerComponent;
import dev.sbs.discordapi.response.component.type.v2.SectionComponent;
import discord4j.core.object.component.IAccessoryComponent;
import discord4j.core.object.component.ICanBeUsedInSectionComponent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Section implements LayoutComponent, TopLevelComponent, ContainerComponent {

    private final @NotNull AccessoryComponent accessory;
    private final @NotNull ConcurrentList<SectionComponent> components;

    public static @NotNull Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Section section = (Section) o;

        return new EqualsBuilder()
            .append(this.getAccessory(), section.getAccessory())
            .append(this.getComponents(), section.getComponents())
            .build();
    }

    public static @NotNull Builder from(@NotNull Section section) {
        return builder()
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
            .append(this.getAccessory())
            .append(this.getComponents())
            .build();
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements ClassBuilder<Section> {

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

        @Override
        public @NotNull Section build() {
            Reflection.validateFlags(this);

            return new Section(
                this.accessory.orElseThrow(),
                this.components.toUnmodifiableList()
            );
        }

    }

}
