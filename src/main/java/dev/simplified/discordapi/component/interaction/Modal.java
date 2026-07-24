package dev.simplified.discordapi.component.interaction;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.exception.InputException;
import dev.simplified.discordapi.component.Component;
import dev.simplified.discordapi.component.capability.EventInteractable;
import dev.simplified.discordapi.component.capability.ModalProcessable;
import dev.simplified.discordapi.component.capability.ModalUpdatable;
import dev.simplified.discordapi.component.capability.UserInteractable;
import dev.simplified.discordapi.component.layout.Label;
import dev.simplified.discordapi.component.scope.ActionComponent;
import dev.simplified.discordapi.component.scope.LabelComponent;
import dev.simplified.discordapi.component.scope.LayoutComponent;
import dev.simplified.discordapi.component.scope.TopLevelModalComponent;
import dev.simplified.discordapi.context.capability.ExceptionContext;
import dev.simplified.discordapi.context.component.ModalContext;
import dev.simplified.discordapi.handler.response.CachedResponse;
import dev.simplified.discordapi.response.Response;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.spec.InteractionPresentModalSpec;
import discord4j.discordjson.possible.Possible;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.AllArgsConstructor;
import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * An immutable modal dialog presented to a user as a pop-up form.
 *
 * <p>
 * Modals contain {@link TopLevelModalComponent} instances - typically {@link Label Labels}
 * wrapping {@link TextInput} or {@link SelectMenu} components. When submitted, the computed
 * {@link #getInteraction()} validates {@link TextInput} values against their validators and
 * dispatches to {@link TextInput.SearchType} handlers before falling back to the modal-level
 * interaction handler.
 *
 * <p>
 * Unlike {@link Button} and {@link SelectMenu}, a modal is not a {@link Component Component}
 * itself; it is presented via {@link #getD4jPresentSpec()} rather than embedded in a message
 * layout.
 *
 * <p>
 * Instances are created via {@link #builder()} and can be copied for modification
 * via {@link #mutate()}.
 *
 * @see TopLevelModalComponent
 * @see TextInput
 * @see Label
 */
@EqualsAndHashCode
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Modal implements EventInteractable<ModalContext>, UserInteractable {

    /** The unique identifier for this modal. */
    private final @NotNull String identifier;

    /** The optional title displayed at the top of the modal. */
    private final @NotNull Optional<String> title;

    /** The top-level modal components contained in this modal. */
    private final @NotNull ConcurrentList<TopLevelModalComponent> components;

    /**
     * Creates a new builder with a random identifier.
     *
     * @return a new {@link Builder} instance
     */
    public static @NotNull Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
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
            .withComponents(modal.getComponents());
    }

    /**
     * Finds the first {@link ActionComponent} of the given type whose extracted property
     * matches the specified value, searching through all layout components in this modal.
     *
     * @param tClass the action component subtype to search for
     * @param function the property extractor applied to each candidate
     * @param value the value to match against the extracted property
     * @param <S> the property type
     * @param <T> the action component subtype
     * @return an {@link Optional} containing the matching component, or empty if none is found
     */
    public <S, T extends ActionComponent> @NotNull Optional<T> findComponent(@NotNull Class<T> tClass, @NotNull Function<T, S> function, S value) {
        return this.getComponents()
            .stream()
            .filter(LayoutComponent.class::isInstance)
            .map(LayoutComponent.class::cast)
            .map(layout -> layout.findComponent(tClass, function, value))
            .flatMap(Optional::stream)
            .findFirst();
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
                    .map(Component::getD4jComponent)
                    .map(discord4j.core.object.component.TopLevelModalComponent.class::cast)
                    .collect(Concurrent.toList())
            )
            .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * First validates each {@link TextInput} value against its validator, raising an
     * {@link InputException} through the exception handler on the first invalid input without
     * running any processor. When all inputs are valid, each inner {@link ModalProcessable}
     * component processes its own folded value in order - there is no modal-level handler; the
     * submit logic lives on the components themselves (see {@link ModalProcessable}).
     */
    @Override
    public @NotNull Function<ModalContext, Mono<Void>> getInteraction() {
        return modalContext -> {
            Modal folded = modalContext.getComponent();

            Optional<TextInput> invalid = folded.innerComponents()
                .filter(TextInput.class::isInstance)
                .map(TextInput.class::cast)
                .filter(textInput -> textInput.getValue().isPresent())
                .filter(textInput -> !textInput.getValidator().test(textInput.getValue().orElseThrow()))
                .findFirst();

            if (invalid.isPresent())
                return modalContext.getDiscordBot().getExceptionHandler().handleException(
                    ExceptionContext.of(
                        modalContext.getDiscordBot(),
                        modalContext,
                        new InputException(invalid.get().getValue()),
                        "Modal Interaction Exception"
                    )
                );

            return Flux.fromStream(folded.innerComponents()
                    .filter(ModalProcessable.class::isInstance)
                    .map(ModalProcessable.class::cast))
                .concatMap(processable -> processable.processModalSubmit(modalContext))
                .then();
        };
    }

    /** Streams this modal's inner components, unwrapping each {@link Label}. */
    private @NotNull Stream<LabelComponent> innerComponents() {
        return this.getComponents()
            .stream()
            .filter(Label.class::isInstance)
            .map(Label.class::cast)
            .map(Label::getComponent);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull ModalContext createContext(@NotNull DiscordBot discordBot, @NotNull ComponentInteractionEvent event, @NotNull Response response, @NotNull Optional<CachedResponse> followup) {
        return ModalContext.of(discordBot, (ModalSubmitInteractionEvent) event, response, this, followup);
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
    public static final class Builder {

        @BuildFlag(nonNull = true)
        private String identifier;
        @BuildFlag(notEmpty = true)
        private Optional<String> title = Optional.empty();
        @BuildFlag(notEmpty = true)
        private final ConcurrentList<TopLevelModalComponent> components = Concurrent.newList();

        /**
         * Clears all components from the {@link Modal}.
         */
        public Builder clearComponents() {
            this.components.clear();
            return this;
        }

        /**
         * Binds a whole-modal submit processor to the first {@link ModalProcessable} component.
         *
         * <p>
         * A modal carries no interaction handler of its own; this convenience attaches a handler
         * that reads the whole submitted modal (via {@link ModalContext#getComponent()}) to the
         * first processable component, so callers with multi-field or single-action modals do not
         * have to pick a component. Prefer a component's own {@code onSubmit} when the handler
         * only needs that component's value.
         *
         * @param processor the processor invoked with the modal submit context
         */
        public Builder onSubmit(@NotNull Function<ModalContext, Mono<Void>> processor) {
            this.components.stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .map(Label::getComponent)
                .filter(ModalProcessable.class::isInstance)
                .map(ModalProcessable.class::cast)
                .findFirst()
                .ifPresent(processable -> processable.bindSubmitProcessor(processor));
            return this;
        }

        /**
         * Updates component values from a modal submit event.
         * <p>
         * Iterates through the event's submitted components and delegates to each
         * matching {@link ModalUpdatable} component's
         * {@link ModalUpdatable#updateFromData updateFromModalData} method.
         *
         * @param event the modal submit interaction event
         */
        public Builder updateComponents(@NotNull ModalSubmitInteractionEvent event) {
            // Discord4J's getComponents(Class) already flattens each action-row/label to its children and
            // filters those by the type, so request the child components directly. Passing LayoutComponent
            // here always yields nothing (children are action components, never layouts), which silently
            // dropped every submitted value.
            event.getComponents(discord4j.core.object.component.MessageComponent.class)
                .forEach(submitted -> submitted.getData().customId().toOptional().ifPresent(customId ->
                    this.components.stream()
                        .filter(Label.class::isInstance)
                        .map(Label.class::cast)
                        .filter(label -> label.getComponent().getIdentifier().equals(customId))
                        .findFirst()
                        .ifPresent(label -> label.getComponent().updateFromData(submitted.getData()))
                ));

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
        public @NotNull Modal build() {
            Reflection.validateFlags(this);

            return new Modal(
                this.identifier,
                this.title,
                this.components
            );
        }

    }

}
