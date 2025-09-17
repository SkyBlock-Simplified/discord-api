package dev.sbs.discordapi.response.component.interaction;

import dev.sbs.api.builder.ClassBuilder;
import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import dev.sbs.api.builder.annotation.BuildFlag;
import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.stream.pair.Pair;
import dev.sbs.api.stream.pair.PairOptional;
import dev.sbs.api.util.StringUtil;
import dev.sbs.discordapi.command.exception.input.InputException;
import dev.sbs.discordapi.context.deferrable.component.ComponentContext;
import dev.sbs.discordapi.context.deferrable.component.modal.ModalContext;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.component.interaction.action.TextInput;
import dev.sbs.discordapi.response.component.layout.Label;
import dev.sbs.discordapi.response.component.type.EventComponent;
import dev.sbs.discordapi.response.component.type.LabelComponent;
import dev.sbs.discordapi.response.component.type.TopLevelModalComponent;
import dev.sbs.discordapi.response.component.type.UserInteractComponent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.spec.InteractionPresentModalSpec;
import discord4j.discordjson.possible.Possible;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
    private final @NotNull String userIdentifier;
    private final @NotNull Optional<String> title;
    private final @NotNull ConcurrentList<TopLevelModalComponent> components;
    private final @NotNull Function<ModalContext, Mono<Void>> interaction;

    public static @NotNull Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Modal modal = (Modal) o;

        return new EqualsBuilder()
            .append(this.getUserIdentifier(), modal.getUserIdentifier())
            .append(this.getTitle(), modal.getTitle())
            .append(this.getComponents(), modal.getComponents())
            .append(this.interaction, modal.interaction)
            .build();
    }

    public static @NotNull Builder from(@NotNull Modal modal) {
        return new Builder()
            .withIdentifier(modal.getUserIdentifier())
            .withTitle(modal.getTitle())
            .withComponents(modal.getComponents())
            .onInteract(modal.interaction);
    }

    public @NotNull InteractionPresentModalSpec getD4jPresentSpec() {
        return InteractionPresentModalSpec.builder()
            .customId(this.getUserIdentifier())
            .title(this.getTitle().map(Possible::of).orElse(Possible.absent()))
            .components(
                this.getComponents()
                    .stream()
                    .map(TopLevelModalComponent::getD4jComponent)
                    .collect(Concurrent.toList())
            )
            .build();
    }

    @Override
    public @NotNull Function<ModalContext, Mono<Void>> getInteraction() {
        return modalContext -> Flux.fromIterable(modalContext.getComponent().getComponents())
            .filter(Label.class::isInstance)
            .map(Label.class::cast)
            .map(Label::getComponent)
            .next()
            //.flatMap(Mono::justOrEmpty)
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
            //.next()
            .switchIfEmpty(this.interaction.apply(modalContext).then(Mono.empty()))
            .flatMap(textInput -> textInput.getSearchType().getInteraction().apply(modalContext, textInput));
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getUserIdentifier())
            .append(this.getTitle())
            .append(this.getComponents())
            .append(this.interaction)
            .build();
    }

    @Override
    public boolean isDeferEdit() {
        return false;
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder implements ClassBuilder<Modal> {

        @BuildFlag(nonNull = true)
        private String identifier;
        @BuildFlag(notEmpty = true)
        private Optional<String> title = Optional.empty();
        @BuildFlag(notEmpty = true)
        private final ConcurrentList<TopLevelModalComponent> components = Concurrent.newList();
        private Optional<Function<ModalContext, Mono<Void>>> interaction = Optional.empty();

        /**
         * Clear all components from {@link Modal}.
         */
        public Builder clearComponents() {
            this.components.clear();
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
        private <S, A extends LabelComponent> PairOptional<Label, A> findComponent(@NotNull Class<A> tClass, @NotNull Function<A, S> function, S value) {
            return PairOptional.of(
                this.components.stream()
                    .filter(Label.class::isInstance)
                    .map(Label.class::cast)
                    .filter(label -> tClass.isInstance(label.getComponent()))
                    .filter(label -> Objects.equals(function.apply(tClass.cast(label.getComponent())), value))
                    .map(label -> Pair.of(label, tClass.cast(label.getComponent())))
                    .findFirst()
            );
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
                            TextInput::getUserIdentifier,
                            d4jComponent.getData().customId().get()
                        ).ifPresent((label, textInput) -> label.mutate()
                            .withComponent(
                                textInput.mutate()
                                    .withValue(
                                        d4jComponent.getData()
                                            .value()
                                            .toOptional()
                                            .map(StringUtil::stripToNull)
                                    )
                                    .build()
                            )
                            .build()
                        );
                        case SELECT_MENU_STRING -> this.findComponent(
                            SelectMenu.class,
                            SelectMenu::getUserIdentifier,
                            d4jComponent.getData().customId().get()
                        ).ifPresent((label, selectMenu) -> selectMenu.updateSelected(
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
         * Add {@link Label Labels} to the {@link Modal}.
         *
         * @param components Variable number of components to add.
         */
        @SuppressWarnings("all")
        public Builder withComponents(@NotNull TopLevelModalComponent... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Add {@link Label Labels} to the {@link Modal}.
         *
         * @param components Collection of components to add.
         */
        public Builder withComponents(@NotNull Iterable<TopLevelModalComponent> components) {
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
            Reflection.validateFlags(this);

            return new Modal(
                this.identifier,
                this.title,
                this.components,
                this.interaction.orElse(NOOP_HANDLER)
            );
        }

    }

}
