package dev.sbs.discordapi.component.interaction;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.tuple.pair.Pair;
import dev.sbs.api.tuple.pair.PairOptional;
import dev.sbs.api.util.StringUtil;
import dev.sbs.api.util.builder.BuildFlag;
import dev.sbs.api.util.builder.ClassBuilder;
import dev.sbs.discordapi.command.exception.InputException;
import dev.sbs.discordapi.component.layout.Label;
import dev.sbs.discordapi.component.type.EventComponent;
import dev.sbs.discordapi.component.type.LabelComponent;
import dev.sbs.discordapi.component.type.TopLevelModalComponent;
import dev.sbs.discordapi.component.type.UserInteractComponent;
import dev.sbs.discordapi.context.ExceptionContext;
import dev.sbs.discordapi.context.component.ComponentContext;
import dev.sbs.discordapi.context.component.ModalContext;
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

/**
 * An immutable modal dialog presented to a user as a pop-up form.
 * <p>
 * Modals contain {@link TopLevelModalComponent} instances - typically {@link Label Labels}
 * wrapping {@link TextInput} or {@link SelectMenu} components. When submitted, the computed
 * {@link #getInteraction()} validates {@link TextInput} values against their validators and
 * dispatches to {@link TextInput.SearchType} handlers before falling back to the modal-level
 * interaction handler.
 * <p>
 * Unlike {@link Button} and {@link SelectMenu}, a modal is not a
 * {@link dev.sbs.discordapi.component.Component Component} itself;
 * it is presented via {@link #getD4jPresentSpec()} rather than embedded in a message layout.
 * <p>
 * Instances are created via {@link #builder()} and can be copied for modification
 * via {@link #mutate()}.
 *
 * @see TextInput
 * @see Label
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Modal implements EventComponent<ModalContext>, UserInteractComponent {

    private static final @NotNull Function<ModalContext, Mono<Void>> NOOP_HANDLER = ComponentContext::deferEdit;

    /** The unique identifier for this modal. */
    private final @NotNull String identifier;

    /** The optional title displayed at the top of the modal. */
    private final @NotNull Optional<String> title;

    /** The top-level modal components contained in this modal. */
    private final @NotNull ConcurrentList<TopLevelModalComponent> components;

    /** The interaction handler invoked when this modal is submitted. */
    private final @NotNull Function<ModalContext, Mono<Void>> interaction;

    /**
     * Creates a new builder with a random identifier.
     *
     * @return a new {@link Builder} instance
     */
    public static @NotNull Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Modal modal = (Modal) o;

        return Objects.equals(this.getIdentifier(), modal.getIdentifier())
            && Objects.equals(this.getTitle(), modal.getTitle())
            && Objects.equals(this.getComponents(), modal.getComponents())
            && Objects.equals(this.interaction, modal.interaction);
    }

    /**
     * Creates a pre-filled builder from the given modal.
     *
     * @param modal the modal to copy fields from
     * @return a pre-filled {@link Builder} instance
     */
    public static @NotNull Builder from(@NotNull Modal modal) {
        return new Builder()
            .withIdentifier(modal.getIdentifier())
            .withTitle(modal.getTitle())
            .withComponents(modal.getComponents())
            .onInteract(modal.interaction);
    }

    /**
     * Converts this modal to a Discord4J presentation specification.
     *
     * @return the Discord4J modal presentation spec
     */
    public @NotNull InteractionPresentModalSpec getD4jPresentSpec() {
        return InteractionPresentModalSpec.builder()
            .customId(this.getIdentifier())
            .title(this.getTitle().map(Possible::of).orElse(Possible.absent()))
            .components(
                this.getComponents()
                    .stream()
                    .map(TopLevelModalComponent::getD4jComponent)
                    .collect(Concurrent.toList())
            )
            .build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Validates each {@link TextInput} value against its validator, dispatches to
     * {@link TextInput.SearchType} handlers for search-enabled inputs, and falls back
     * to the modal-level interaction handler for remaining submissions.
     */
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
        return Objects.hash(this.getIdentifier(), this.getTitle(), this.getComponents(), this.interaction);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDeferEdit() {
        return false;
    }

    /**
     * Creates a pre-filled builder from this instance for modification.
     *
     * @return a pre-filled {@link Builder} instance
     */
    public @NotNull Builder mutate() {
        return from(this);
    }

    /**
     * A builder for constructing {@link Modal} instances.
     */
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
         * Clears all components from the {@link Modal}.
         */
        public Builder clearComponents() {
            this.components.clear();
            return this;
        }

        /**
         * Finds the first {@link LabelComponent} matching the given predicate within the modal's labels.
         *
         * @param tClass the component type to match
         * @param function the accessor used to extract the comparison value
         * @param value the value to match against
         * @param <S> the comparison type
         * @param <A> the label component type
         * @return the matching label and component pair, if present
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
         * Sets the interaction handler invoked when the {@link Modal} is submitted.
         *
         * @param interaction the interaction function, or {@code null} for the default no-op handler
         */
        public Builder onInteract(@Nullable Function<ModalContext, Mono<Void>> interaction) {
            return this.onInteract(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction handler invoked when the {@link Modal} is submitted.
         *
         * @param interaction the optional interaction function
         */
        public Builder onInteract(@NotNull Optional<Function<ModalContext, Mono<Void>>> interaction) {
            this.interaction = interaction;
            return this;
        }

        /**
         * Updates component values from a modal submit event.
         * <p>
         * Iterates through the event's submitted components and updates matching
         * {@link TextInput} values and {@link SelectMenu} selections within this builder.
         *
         * @param event the modal submit interaction event
         */
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
                            SelectMenu::getIdentifier,
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
         * Adds {@link TopLevelModalComponent components} to the {@link Modal}.
         *
         * @param components variable number of components to add
         */
        @SuppressWarnings("all")
        public Builder withComponents(@NotNull TopLevelModalComponent... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Adds {@link TopLevelModalComponent components} to the {@link Modal}.
         *
         * @param components collection of components to add
         */
        public Builder withComponents(@NotNull Iterable<TopLevelModalComponent> components) {
            components.forEach(this.components::add);
            return this;
        }

        /**
         * Sets the identifier of the {@link Modal}, overriding the default random UUID.
         *
         * @param identifier the identifier to use
         */
        public Builder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Sets the title displayed at the top of the {@link Modal}.
         *
         * @param title the title text, or {@code null} to clear
         */
        public Builder withTitle(@Nullable String title) {
            return this.withTitle(Optional.ofNullable(title));
        }

        /**
         * Sets the title displayed at the top of the {@link Modal}.
         *
         * @param title the optional title text
         */
        public Builder withTitle(@NotNull Optional<String> title) {
            this.title = title;
            return this;
        }

        /**
         * Builds a new {@link Modal} from the configured fields.
         *
         * @return a new {@link Modal} instance
         */
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
