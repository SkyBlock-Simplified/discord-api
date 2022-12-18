package dev.sbs.discordapi.response.component.interaction;

import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.context.interaction.deferrable.component.ComponentContext;
import dev.sbs.discordapi.context.interaction.deferrable.component.modal.ModalContext;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;
import dev.sbs.discordapi.response.component.type.D4jComponent;
import dev.sbs.discordapi.response.component.type.InteractableComponent;
import dev.sbs.discordapi.response.component.type.PreservableComponent;
import discord4j.core.spec.InteractionPresentModalSpec;
import discord4j.discordjson.possible.Possible;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Modal extends InteractionComponent implements InteractableComponent<ModalContext> {

    private static final Function<ModalContext, Mono<Void>> NOOP_HANDLER = ComponentContext::deferEdit;
    @Getter private final @NotNull UUID uniqueId;
    @Getter private final @NotNull Optional<String> title;
    @Getter private final @NotNull ConcurrentList<LayoutComponent<ActionComponent>> components;
    @Getter private final boolean isPaging = false;
    private final @NotNull Optional<Function<ModalContext, Mono<Void>>> modalInteraction;

    public static ModalBuilder from(@NotNull Modal modal) {
        return new ModalBuilder(modal.getUniqueId());
    }

    public InteractionPresentModalSpec getD4jPresentSpec() {
        return InteractionPresentModalSpec.builder()
            .customId(this.getUniqueId().toString())
            .title(this.getTitle().map(Possible::of).orElse(Possible.absent()))
            .components(this.getComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .build();
    }

    @Override
    public @NotNull Function<ModalContext, Mono<Void>> getInteraction() {
        return this.modalInteraction.orElse(NOOP_HANDLER);
    }

    @Override
    public boolean isDeferEdit() {
        return false;
    }

    public static ModalBuilder builder() {
        return new ModalBuilder(UUID.randomUUID());
    }

    public ModalBuilder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ModalBuilder implements Builder<Modal> {

        private final UUID uniqueId;
        private Optional<String> title = Optional.empty();
        private final ConcurrentList<LayoutComponent<ActionComponent>> components = Concurrent.newList();
        private Optional<Function<ModalContext, Mono<Void>>> interaction = Optional.empty();

        /**
         * Clear all but preservable components from {@link Modal}.
         */
        public ModalBuilder clearComponents() {
            return this.clearComponents(true);
        }

        /**
         * Clear all components from {@link Modal}.
         *
         * @param enforcePreserve True to leave preservable components.
         */
        public ModalBuilder clearComponents(boolean enforcePreserve) {
            // Remove Possibly Preserved Components
            this.components.stream()
                .filter(layoutComponent -> !enforcePreserve || layoutComponent.notPreserved())
                .forEach(layoutComponent -> layoutComponent.getComponents()
                    .stream()
                    .filter(PreservableComponent.class::isInstance)
                    .map(PreservableComponent.class::cast)
                    .filter(component -> !enforcePreserve || component.notPreserved())
                    .forEach(component -> layoutComponent.getComponents().remove(component))
                );

            // Remove Empty Layout Components
            this.components.removeIf(layoutComponent -> layoutComponent.getComponents().isEmpty());
            return this;
        }

        /**
         * Updates an existing {@link ActionComponent}.
         *
         * @param actionComponent The component to edit.
         */
        public ModalBuilder editComponent(@NotNull ActionComponent actionComponent) {
            this.components.forEach(layoutComponent -> layoutComponent.getComponents()
                .stream()
                .filter(actionComponent.getClass()::isInstance)
                .map(actionComponent.getClass()::cast)
                .filter(innerComponent -> innerComponent.getUniqueId().equals(actionComponent.getUniqueId()))
                .findFirst()
                .ifPresent(innerComponent -> layoutComponent.getComponents().set(
                    layoutComponent.getComponents().indexOf(innerComponent),
                    actionComponent
                ))
            );

            return this;
        }

        /**
         * Finds an existing {@link ActionComponent}.
         *
         * @param tClass The component type to match.
         * @param function The method reference to match with.
         * @param value The value to match with.
         * @return The matching component, if it exists.
         */
        public <S, A extends InteractionComponent & PreservableComponent & D4jComponent> Optional<A> findComponent(@NotNull Class<A> tClass, @NotNull Function<A, S> function, S value) {
            return this.components.stream()
                .flatMap(layoutComponent -> layoutComponent.getComponents()
                    .stream()
                    .filter(tClass::isInstance)
                    .map(tClass::cast)
                    .filter(innerComponent -> Objects.equals(function.apply(innerComponent), value))
                )
                .findFirst();
        }

        /**
         * Sets the interaction to execute when the {@link Modal} is submitted by the user.
         *
         * @param interaction The interaction function.
         */
        public ModalBuilder onInteract(@Nullable Function<ModalContext, Mono<Void>> interaction) {
            return this.onInteract(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction to execute when the {@link Modal} is submitted by the user.
         *
         * @param interaction The interaction function.
         */
        public ModalBuilder onInteract(@NotNull Optional<Function<ModalContext, Mono<Void>>> interaction) {
            this.interaction = interaction;
            return this;
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the {@link Modal}.
         *
         * @param components Variable number of layout components to add.
         */
        @SuppressWarnings("all")
        public ModalBuilder withComponents(@NotNull LayoutComponent<ActionComponent>... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the {@link Modal}.
         *
         * @param components Collection of layout components to add.
         */
        public ModalBuilder withComponents(@NotNull Iterable<LayoutComponent<ActionComponent>> components) {
            components.forEach(this.components::add);
            return this;
        }

        /**
         * Sets the title of the {@link Modal}.
         *
         * @param title The title of the modal.
         */
        public ModalBuilder withTitle(@Nullable String title) {
            return this.withTitle(Optional.ofNullable(title));
        }

        /**
         * Sets the title of the {@link Modal}.
         *
         * @param title The title of the modal.
         */
        public ModalBuilder withTitle(@NotNull Optional<String> title) {
            this.title = title;
            return this;
        }

        @Override
        public Modal build() {
            return new Modal(
                this.uniqueId,
                this.title,
                this.components,
                this.interaction
            );
        }

    }

}
