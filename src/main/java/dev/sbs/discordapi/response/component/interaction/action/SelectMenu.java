package dev.sbs.discordapi.response.component.interaction.action;

import dev.sbs.api.builder.ClassBuilder;
import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import dev.sbs.api.builder.annotation.BuildFlag;
import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.StringUtil;
import dev.sbs.discordapi.context.deferrable.component.action.OptionContext;
import dev.sbs.discordapi.context.deferrable.component.action.SelectMenuContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.type.EventComponent;
import dev.sbs.discordapi.response.component.type.ToggleableComponent;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public interface SelectMenu extends ActionComponent, EventComponent<SelectMenuContext>, ToggleableComponent {

    @NotNull Optional<String> getPlaceholder();

    int getMinValues();

    int getMaxValues();

    boolean isPlaceholderShowingSelectedOption();

    boolean isDeferEdit();

    @NotNull Optional<Function<SelectMenuContext, Mono<Void>>> getUserInteraction();


    @NotNull ConcurrentList<Option> getOptions();

    @Getter(AccessLevel.NONE)
    private final @NotNull ConcurrentList<Option> selected = Concurrent.newList();

    static @NotNull StringSelectMenu.StringBuilder string() {
        return StringSelectMenu.builder();
    }

    static @NotNull EntitySelectMenu.Builder entity() {
        return EntitySelectMenu.builder();
    }

    /**
     * Finds an existing {@link Option}.
     *
     * @param function The method reference to match with.
     * @param value The value to match with.
     * @return The matching option, if it exists.
     */
    default <S> Optional<Option> findOption(@NotNull Function<Option, S> function, S value) {
        return this.getOptions().stream()
            .filter(option -> Objects.equals(function.apply(option), value))
            .findFirst();
    }

    @Override
    default @NotNull discord4j.core.object.component.SelectMenu getD4jComponent() {
        return discord4j.core.object.component.SelectMenu.of(
                this.getUserIdentifier(),
                this.getOptions()
                    .stream()
                    .map(option -> option.getD4jOption(this.getSelected().contains(option)))
                    .collect(Concurrent.toList())
            )
            .withPlaceholder(this.getPlaceholder().orElse(""))
            .withMinValues(this.getMinValues())
            .withMaxValues(this.getMaxValues())
            .disabled(this.isDisabled());
    }

    @Override
    default @NotNull Function<SelectMenuContext, Mono<Void>> getInteraction() {
        return selectMenuContext -> Mono.just(selectMenuContext)
            //.doOnNext(context -> this.updateSelected(context.getEvent().getValues()))
            .flatMap(context -> Mono.justOrEmpty(this.getUserInteraction())
                .flatMap(interaction -> interaction.apply(context))
                .thenReturn(context)
            )
            .filter(context -> context.getEvent().getValues().size() == 1)
            .flatMap(context -> Mono.justOrEmpty(this.getSelected().getFirst())
                .flatMap(option -> option.getInteraction().apply(OptionContext.of(context, context.getResponse(), option)))
                .switchIfEmpty(context.deferEdit())
            );
    }

    public @NotNull ConcurrentList<Option> getSelected() {
        return this.selected.toUnmodifiableList();
    }

    @NotNull Builder mutate();

    default @NotNull SelectMenu updateSelected() {
        return this.updateSelected(Concurrent.newList());
    }

    default @NotNull SelectMenu updateSelected(@NotNull String... values) {
        return this.updateSelected(Arrays.asList(values));
    }

    default @NotNull SelectMenu updateSelected(@NotNull List<String> values) {
        this.selected.clear();
        this.selected.addAll(
            values.stream()
                .map(value -> this.findOption(Option::getValue, value))
                .flatMap(Optional::stream)
                .collect(Concurrent.toList())
        );
        return this;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    abstract class Builder implements ClassBuilder<SelectMenu> {

        @BuildFlag(nonNull = true)
        protected String identifier;
        protected boolean enabled;
        protected Optional<String> placeholder = Optional.empty();
        protected boolean placeholderShowingSelectedOption;
        @Range(from = 0, to = Option.MAX_ALLOWED)
        protected int minValues = 1;
        @Range(from = 1, to = Option.MAX_ALLOWED)
        protected int maxValues = 1;
        @BuildFlag(limit = Option.MAX_ALLOWED)
        protected final ConcurrentList<Option> options = Concurrent.newList();
        protected boolean deferEdit;
        protected Optional<Function<SelectMenuContext, Mono<Void>>> interaction = Optional.empty();

        /**
         * Updates an existing {@link Option}.
         *
         * @param option The option to update.
         */
        public Builder editOption(@NotNull Option option) {
            this.options.stream()
                .filter(innerOption -> innerOption.getUniqueId().equals(option.getUniqueId()))
                .findFirst()
                .ifPresent(innerOption -> {
                    int index = this.options.indexOf(innerOption);
                    this.options.remove(index);
                    this.options.add(index, option);
                });
            return this;
        }

        /**
         * Finds an existing {@link Option}.
         *
         * @param function The method reference to match with.
         * @param value The value to match with.
         * @return The matching option, if it exists.
         */
        public final <S> Optional<Option> findOption(@NotNull Function<Option, S> function, S value) {
            return this.options.stream()
                .filter(option -> Objects.equals(function.apply(option), value))
                .findFirst();
        }

        /**
         * Sets the interaction to execute when the {@link StringSelectMenu} is interacted with by a user.
         *
         * @param interaction The interaction function.
         */
        public Builder onInteract(@Nullable Function<SelectMenuContext, Mono<Void>> interaction) {
            return this.onInteract(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction to execute when the {@link StringSelectMenu} is interacted with by a user.
         *
         * @param interaction The interaction function.
         */
        public Builder onInteract(@NotNull Optional<Function<SelectMenuContext, Mono<Void>>> interaction) {
            this.interaction = interaction;
            return this;
        }

        /**
         * Sets the {@link StringSelectMenu} as disabled.
         */
        public Builder setDisabled() {
            return this.setDisabled(true);
        }

        /**
         * Sets if the {@link StringSelectMenu} should be disabled.
         *
         * @param value True to disable the select menu.
         */
        public Builder setDisabled(boolean value) {
            return this.setEnabled(!value);
        }

        /**
         * Sets this {@link StringSelectMenu} as deferred when interacting.
         */
        public Builder withDeferEdit() {
            return this.withDeferEdit(true);
        }

        /**
         * Sets whether this {@link StringSelectMenu} is deferred when interacting.
         *
         * @param deferEdit True to defer interaction.
         */
        public Builder withDeferEdit(boolean deferEdit) {
            this.deferEdit = deferEdit;
            return this;
        }

        /**
         * Sets the {@link StringSelectMenu} as enabled.
         */
        public Builder setEnabled() {
            return this.setEnabled(true);
        }

        /**
         * Sets if the {@link StringSelectMenu} should be enabled.
         *
         * @param value True to enable the button.
         */
        public Builder setEnabled(boolean value) {
            this.enabled = value;
            return this;
        }

        /**
         * Overrides the default identifier of the {@link StringSelectMenu}.
         *
         * @param identifier The identifier to use.
         */
        public Builder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Overrides the default identifier of the {@link StringSelectMenu}.
         *
         * @param identifier The identifier to use.
         * @param args The objects used to format the identifier.
         */
        public Builder withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
            this.identifier = String.format(identifier, args);
            return this;
        }

        /**
         * Add {@link Option Options} to the {@link StringSelectMenu}.
         *
         * @param options Variable number of options to add.
         */
        public Builder withOptions(@NotNull Option... options) {
            return this.withOptions(Arrays.asList(options));
        }

        /**
         * Add {@link Option Options} {@link StringSelectMenu}.
         *
         * @param options Collection of options to add.
         */
        public Builder withOptions(@NotNull Iterable<Option> options) {
            options.forEach(this.options::add);
            return this;
        }

        /**
         * Sets the placeholder text to show on the {@link StringSelectMenu}.
         *
         * @param placeholder The placeholder text to use.
         */
        public Builder withPlaceholder(@Nullable String placeholder) {
            return this.withPlaceholder(Optional.ofNullable(placeholder));
        }

        /**
         * Sets the placeholder text to show on the {@link StringSelectMenu}.
         *
         * @param placeholder The placeholder text to use.
         */
        public Builder withPlaceholder(@NotNull Optional<String> placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        /**
         * Sets the minimum number of options that need to be selected for the {@link StringSelectMenu}.
         *
         * @param minValues The minimum number of selected {@link Option Options}.
         */
        public Builder withMinValues(int minValues) {
            this.minValues = minValues;
            return this;
        }

        /**
         * Sets the maximum number of options that need to be selected for the {@link StringSelectMenu}.
         *
         * @param maxValues The maximum number of selected {@link Option Options}.
         */
        public Builder withMaxValues(int maxValues) {
            this.maxValues = maxValues;
            return this;
        }

        /**
         * Sets the {@link StringSelectMenu} to override its placeholder with the selected {@link Option}.
         */
        public Builder withPlaceholderShowingSelectedOption() {
            return this.withPlaceholderShowingSelectedOption(true);
        }

        /**
         * Sets if the {@link StringSelectMenu} should override its placeholder with the selected {@link Option}.
         *
         * @param value True to override the placeholder with the selected option.
         */
        public Builder withPlaceholderShowingSelectedOption(boolean value) {
            this.placeholderShowingSelectedOption = value;
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link StringSelectMenu} component.
         */
        @Override
        public abstract @NotNull SelectMenu build();

    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class Option {

        public static final int MAX_ALLOWED = 25;
        private static final Function<OptionContext, Mono<Void>> NOOP_HANDLER = __ -> Mono.empty();
        private final @NotNull UUID uniqueId;
        private final @NotNull String label;
        private final @NotNull String value;
        private final @NotNull Optional<String> description;
        private final @NotNull Optional<Emoji> emoji;
        private final @NotNull Function<OptionContext, Mono<Void>> interaction;

        public static @NotNull Builder builder() {
            return new Builder(UUID.randomUUID());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Option option = (Option) o;

            return new EqualsBuilder()
                .append(this.getUniqueId(), option.getUniqueId())
                .append(this.getLabel(), option.getLabel())
                .append(this.getValue(), option.getValue())
                .append(this.getDescription(), option.getDescription())
                .append(this.getEmoji(), option.getEmoji())
                .build();
        }

        public static @NotNull Builder from(@NotNull Option option) {
            return new Builder(option.getUniqueId())
                .withLabel(option.getLabel())
                .withValue(option.getValue())
                .withDescription(option.getDescription())
                .withEmoji(option.getEmoji())
                .onInteract(option.interaction);
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                .append(this.getUniqueId())
                .append(this.getLabel())
                .append(this.getValue())
                .append(this.getDescription())
                .append(this.getEmoji())
                .build();
        }

        public @NotNull discord4j.core.object.component.SelectMenu.Option getD4jOption(boolean selected) {
            discord4j.core.object.component.SelectMenu.Option d4jOption = discord4j.core.object.component.SelectMenu.Option.of(this.getLabel(), this.getValue())
                .withDescription(this.getDescription().orElse(""))
                .withDefault(selected);

            if (this.getEmoji().isPresent())
                d4jOption = d4jOption.withEmoji(this.getEmoji().get().getD4jReaction());

            return d4jOption;
        }

        public @NotNull Builder mutate() {
            return from(this);
        }

        @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
        public static final class Builder implements ClassBuilder<Option> {

            private final UUID uniqueId;
            private Optional<String> label = Optional.empty();
            private Optional<String> value = Optional.empty();
            private Optional<String> description = Optional.empty();
            private Optional<Emoji> emoji = Optional.empty();
            private Optional<Function<OptionContext, Mono<Void>>> interaction = Optional.empty();

            /**
             * Sets the interaction to execute when the {@link Option} is interacted with by a user.
             * <br><br>
             * This only executes if the enclosing {@link SelectMenu} is limited to 1 {@link Option}.
             * <br><br>
             * See {@link SelectMenu#getMinValues()} and {@link SelectMenu#getMaxValues()}.
             *
             * @param interaction The interaction consumer.
             */
            public Builder onInteract(@Nullable Function<OptionContext, Mono<Void>> interaction) {
                return this.onInteract(Optional.ofNullable(interaction));
            }

            /**
             * Sets the interaction to execute when the {@link Option} is interacted with by a user.
             * <br><br>
             * This only executes if the enclosing {@link SelectMenu} is limited to 1 {@link Option}.
             * <br><br>
             * See {@link SelectMenu#getMinValues()} and {@link SelectMenu#getMaxValues()}.
             *
             * @param interaction The interaction consumer.
             */
            public Builder onInteract(@NotNull Optional<Function<OptionContext, Mono<Void>>> interaction) {
                this.interaction = interaction;
                return this;
            }

            /**
             * Sets the description of the {@link Option} shown under the label.
             *
             * @param description The description of the option.
             */
            public Builder withDescription(@Nullable String description) {
                return this.withDescription(Optional.ofNullable(description));
            }

            /**
             * Sets the description of the {@link Option} shown under the label.
             *
             * @param description The description of the option.
             * @param args The objects used to format the description.
             */
            public Builder withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
                return this.withDescription(StringUtil.formatNullable(description, args));
            }

            /**
             * Sets the description of the {@link Option} shown under the label.
             *
             * @param description The description of the option.
             */
            public Builder withDescription(@NotNull Optional<String> description) {
                this.description = description;
                return this;
            }

            /**
             * Sets the {@link Emoji} shown to the left of the label.
             *
             * @param emoji The emoji of the option.
             */
            public Builder withEmoji(@Nullable Emoji emoji) {
                return this.withEmoji(Optional.ofNullable(emoji));
            }

            /**
             * Sets the {@link Emoji} shown to the left of the label.
             *
             * @param emoji The emoji of the option.
             */
            public Builder withEmoji(@NotNull Optional<Emoji> emoji) {
                this.emoji = emoji;
                return this;
            }

            /**
             * Sets the label text of the {@link Option}.
             *
             * @param label The label of the option.
             */
            public Builder withLabel(@NotNull String label) {
                this.label = Optional.of(label);
                return this;
            }

            /**
             * Sets the label text of the {@link Option}.
             *
             * @param label The label of the option.
             */
            public Builder withLabel(@PrintFormat @NotNull String label, @Nullable Object... args) {
                this.label = Optional.of(String.format(label, args));
                return this;
            }

            /**
             * Sets the value of the {@link Option}.
             *
             * @param value The option value.
             */
            public Builder withValue(@NotNull String value) {
                this.value = Optional.of(value);
                return this;
            }

            /**
             * Sets the value of the {@link Option}.
             *
             * @param value The option value.
             */
            public Builder withValue(@PrintFormat @NotNull String value, @Nullable Object... args) {
                this.value = Optional.of(String.format(value, args));
                return this;
            }

            /**
             * Build using the configured fields.
             *
             * @return A built {@link SelectMenu} component.
             */
            @Override
            public @NotNull Option build() {
                return new Option(
                    this.uniqueId,
                    this.label.orElse(this.uniqueId.toString()),
                    this.value.orElse(this.uniqueId.toString()),
                    this.description,
                    this.emoji,
                    this.interaction.orElse(NOOP_HANDLER)
                );
            }

        }
        
    }

}
