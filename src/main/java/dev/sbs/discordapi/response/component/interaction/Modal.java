package dev.sbs.discordapi.response.component.interaction;

import dev.sbs.api.util.builder.annotation.BuildFlag;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.context.deferrable.component.ComponentContext;
import dev.sbs.discordapi.context.deferrable.component.modal.ModalContext;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.component.interaction.action.TextInput;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;
import dev.sbs.discordapi.response.component.type.IdentifiableComponent;
import dev.sbs.discordapi.response.component.type.InteractableComponent;
import dev.sbs.discordapi.response.component.type.PreservableComponent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Modal extends Component implements IdentifiableComponent, InteractableComponent<ModalContext> {

    private static final Function<ModalContext, Mono<Void>> NOOP_HANDLER = ComponentContext::deferEdit;
    private final @NotNull String identifier;
    private final @NotNull Optional<String> title;
    private final @NotNull ConcurrentList<LayoutComponent<ActionComponent>> components;
    private final @NotNull PageType pageType;
    private final @NotNull Function<ModalContext, Mono<Void>> interaction;

    public static @NotNull Builder builder() {
        return new Builder(UUID.randomUUID().toString());
    }

    /**
     * Finds an existing {@link ActionComponent}.
     *
     * @param tClass The component type to match.
     * @param function The method reference to match with.
     * @param value The value to match with.
     * @return The matching component, if it exists.
     */
    public <S, A extends ActionComponent> @NotNull Optional<A> findComponent(@NotNull Class<A> tClass, @NotNull Function<A, S> function, S value) {
        return this.getComponents()
            .stream()
            .flatMap(layoutComponent -> layoutComponent.getComponents()
                .stream()
                .filter(tClass::isInstance)
                .map(tClass::cast)
                .filter(innerComponent -> Objects.equals(function.apply(innerComponent), value))
            )
            .findFirst();
    }

    /**
     * Searches for a {@link ActionComponent} by its identifier.
     *
     * @param identifier The identifier to search for.
     * @return The matching component, if it exists.
     */
    public <A extends ActionComponent> @NotNull Optional<A> findComponent(@NotNull Class<A> tClass, @NotNull String identifier) {
        return this.findComponent(tClass, IdentifiableComponent::getIdentifier, identifier);
    }

    public static @NotNull Builder from(@NotNull Modal modal) {
        return new Builder(modal.getIdentifier())
            .withTitle(modal.getTitle())
            .withComponents(modal.getComponents())
            .withPageType(modal.getPageType())
            .onInteract(modal.getInteraction());
    }

    public @NotNull InteractionPresentModalSpec getD4jPresentSpec() {
        return InteractionPresentModalSpec.builder()
            .customId(this.getIdentifier())
            .title(this.getTitle().map(Possible::of).orElse(Possible.absent()))
            .components(this.getComponents().stream().map(LayoutComponent::getD4jComponent).collect(Concurrent.toList()))
            .build();
    }

    @Override
    public boolean isDeferEdit() {
        return false;
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder implements dev.sbs.api.util.builder.Builder<Modal> {

        private final String identifier;
        private Optional<String> title = Optional.empty();
        private final ConcurrentList<LayoutComponent<ActionComponent>> components = Concurrent.newList();
        @BuildFlag(nonNull = true)
        private PageType pageType = PageType.NONE;
        private Optional<Function<ModalContext, Mono<Void>>> interaction = Optional.empty();

        /**
         * Clear all but preservable components from {@link Modal}.
         */
        public Builder clearComponents() {
            return this.clearComponents(true);
        }

        /**
         * Clear all components from {@link Modal}.
         *
         * @param enforcePreserve True to leave preservable components.
         */
        public Builder clearComponents(boolean enforcePreserve) {
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
        public Builder editComponent(@NotNull ActionComponent actionComponent) {
            this.components.forEach(layoutComponent -> layoutComponent.getComponents()
                .stream()
                .filter(actionComponent.getClass()::isInstance)
                .map(actionComponent.getClass()::cast)
                .filter(innerComponent -> innerComponent.getIdentifier().equals(actionComponent.getIdentifier()))
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
        public <S, A extends ActionComponent> Optional<A> findComponent(@NotNull Class<A> tClass, @NotNull Function<A, S> function, S value) {
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
        public Builder onInteract(@Nullable Function<ModalContext, Mono<Void>> interaction) {
            return this.onInteract(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction to execute when the {@link Modal} is submitted by the user.
         *
         * @param interaction The interaction function.
         */
        public Builder onInteract(@NotNull Optional<Function<ModalContext, Mono<Void>>> interaction) {
            this.interaction = interaction;
            return this;
        }

        public Builder updateComponents(@NotNull ModalSubmitInteractionEvent event) {
            event.getComponents()
                .stream()
                .filter(discord4j.core.object.component.LayoutComponent.class::isInstance)
                .map(discord4j.core.object.component.LayoutComponent.class::cast)
                .map(discord4j.core.object.component.LayoutComponent::getChildren)
                .flatMap(List::stream)
                .forEach(d4jComponent -> {
                    switch (d4jComponent.getType()) {
                        case TEXT_INPUT -> this.findComponent(
                            TextInput.class,
                            TextInput::getIdentifier,
                            d4jComponent.getData().customId().get()
                        ).ifPresent(textInput -> this.editComponent(
                            textInput.mutate()
                                .withValue(d4jComponent.getData().value().toOptional())
                                .build()
                        ));
                        case SELECT_MENU -> this.findComponent(
                            SelectMenu.class,
                            SelectMenu::getIdentifier,
                            d4jComponent.getData().customId().get()
                        ).ifPresent(selectMenu -> selectMenu.updateSelected(
                            d4jComponent.getData()
                                .values()
                                .toOptional()
                                .orElse(Concurrent.newList())
                        ));
                    }
                });

            return this;
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the {@link Modal}.
         *
         * @param components Variable number of layout components to add.
         */
        @SuppressWarnings("all")
        public Builder withComponents(@NotNull LayoutComponent<ActionComponent>... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the {@link Modal}.
         *
         * @param components Collection of layout components to add.
         */
        public Builder withComponents(@NotNull Iterable<LayoutComponent<ActionComponent>> components) {
            components.forEach(this.components::add);
            return this;
        }

        /**
         * Sets the page type of the {@link TextInput}.
         *
         * @param pageType The page type of the text input.
         */
        public Builder withPageType(@NotNull PageType pageType) {
            this.pageType = pageType;
            return this;
        }

        /**
         * Sets the title of the {@link Modal}.
         *
         * @param title The title of the modal.
         */
        public Builder withTitle(@Nullable String title) {
            return this.withTitle(Optional.ofNullable(title));
        }

        /**
         * Sets the title of the {@link Modal}.
         *
         * @param title The title of the modal.
         */
        public Builder withTitle(@NotNull Optional<String> title) {
            this.title = title;
            return this;
        }

        @Override
        public @NotNull Modal build() {
            return new Modal(
                this.identifier,
                this.title,
                this.components,
                this.pageType,
                this.interaction.orElse(NOOP_HANDLER)
            );
        }

    }

    public enum PageType {

        NONE,
        SEARCH

    }

}
