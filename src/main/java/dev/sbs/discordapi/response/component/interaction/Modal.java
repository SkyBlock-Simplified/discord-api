package dev.sbs.discordapi.response.component.interaction;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.StringUtil;
import dev.sbs.discordapi.command.exception.input.InputException;
import dev.sbs.discordapi.context.deferrable.component.ComponentContext;
import dev.sbs.discordapi.context.deferrable.component.modal.ModalContext;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.component.interaction.action.TextInput;
import dev.sbs.discordapi.response.component.layout.LayoutComponent;
import dev.sbs.discordapi.response.component.type.EventComponent;
import dev.sbs.discordapi.response.component.type.UserInteractComponent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.spec.InteractionPresentModalSpec;
import discord4j.discordjson.possible.Possible;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Modal implements EventComponent<ModalContext>, UserInteractComponent {

    private static final @NotNull Function<ModalContext, Mono<Void>> NOOP_HANDLER = ComponentContext::deferEdit;
    private final @NotNull String identifier;
    private final @NotNull Optional<String> title;
    private final @NotNull ConcurrentList<LayoutComponent> components;
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
        return this.findComponent(tClass, Component::getIdentifier, identifier);
    }

    public static @NotNull Builder from(@NotNull Modal modal) {
        return new Builder(modal.getIdentifier())
            .withTitle(modal.getTitle())
            .withComponents(modal.getComponents())
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
    public @NotNull Function<ModalContext, Mono<Void>> getInteraction() {
        return modalContext -> Flux.fromIterable(modalContext.getComponent().getComponents())
            .map(LayoutComponent::getComponents)
            .map(ConcurrentList::getFirst)
            .flatMap(Mono::justOrEmpty)
            .map(TextInput.class::cast)
            .filter(textInput -> textInput.getValue().isPresent())
            .flatMap(textInput -> {
                boolean validInput = textInput.getValue()
                    .map(value -> textInput.getValidator().test(value))
                    .orElse(true);

                if (!validInput) {
                    return modalContext.getDiscordBot().getExceptionHandler().handleException(
                        ExceptionContext.of(
                            modalContext.getDiscordBot(),
                            modalContext,
                            new InputException(textInput.getValue()),
                            "Modal Interaction Exception"
                        )
                    );
                }

                return Mono.just(textInput);
            })
            // Search Checks Top-Most
            .filter(textInput -> textInput.getSearchType() != TextInput.SearchType.NONE)
            .next()
            .switchIfEmpty(this.interaction.apply(modalContext).then(Mono.empty()))
            .flatMap(textInput -> textInput.getSearchType().getInteraction().apply(modalContext, textInput));
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
        private final ConcurrentList<LayoutComponent> components = Concurrent.newList();
        private Optional<Function<ModalContext, Mono<Void>>> interaction = Optional.empty();

        /**
         * Clear all components from {@link Modal}.
         */
        public Builder clearComponents() {
            this.components.clear();
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
                                .withValue(
                                    d4jComponent.getData()
                                        .value()
                                        .toOptional()
                                        .map(StringUtil::stripToNull)
                                )
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
        public Builder withComponents(@NotNull LayoutComponent... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the {@link Modal}.
         *
         * @param components Collection of layout components to add.
         */
        public Builder withComponents(@NotNull Iterable<LayoutComponent> components) {
            components.forEach(this.components::add);
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
                this.interaction.orElse(NOOP_HANDLER)
            );
        }

    }

}
