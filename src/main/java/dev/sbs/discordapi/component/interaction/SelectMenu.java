package dev.sbs.discordapi.component.interaction;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.StringUtil;
import dev.sbs.api.util.builder.BuildFlag;
import dev.sbs.api.util.builder.ClassBuilder;
import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.type.EventComponent;
import dev.sbs.discordapi.component.type.LabelComponent;
import dev.sbs.discordapi.component.type.ToggleableComponent;
import dev.sbs.discordapi.context.component.OptionContext;
import dev.sbs.discordapi.context.component.SelectMenuContext;
import dev.sbs.discordapi.response.Emoji;
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

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class SelectMenu implements ActionComponent, EventComponent<SelectMenuContext>, LabelComponent, ToggleableComponent {

    private final @NotNull String identifier;
    private final @NotNull Optional<String> placeholder;
    private final int minValues;
    private final int maxValues;
    private final boolean placeholderShowingSelectedOption;
    private final @NotNull ConcurrentList<Option> options;
    private final boolean deferEdit;
    private final boolean required;
    @Getter(AccessLevel.NONE)
    private final @NotNull Optional<Function<SelectMenuContext, Mono<Void>>> userInteraction;
    private final @NotNull Type menuType;
    private @NotNull ConcurrentList<Option> selected;
    private boolean enabled;

    public static @NotNull Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        SelectMenu that = (SelectMenu) o;

        return this.getMinValues() == that.getMinValues()
            && this.getMaxValues() == that.getMaxValues()
            && this.isPlaceholderShowingSelectedOption() == that.isPlaceholderShowingSelectedOption()
            && this.isDeferEdit() == that.isDeferEdit()
            && this.isRequired() == that.isRequired()
            && this.isEnabled() == that.isEnabled()
            && Objects.equals(this.getIdentifier(), that.getIdentifier())
            && Objects.equals(this.getPlaceholder(), that.getPlaceholder())
            && Objects.equals(this.getOptions(), that.getOptions())
            && Objects.equals(this.userInteraction, that.userInteraction)
            && Objects.equals(this.getMenuType(), that.getMenuType())
            && Objects.equals(this.getSelected(), that.getSelected());
    }

    /**
     * Finds an existing {@link Option}.
     *
     * @param function The method reference to match with.
     * @param value The value to match with.
     * @return The matching option, if it exists.
     */
    public <S> Optional<Option> findOption(@NotNull Function<Option, S> function, S value) {
        return this.getOptions().stream()
            .filter(option -> Objects.equals(function.apply(option), value))
            .findFirst();
    }

    public static @NotNull Builder from(@NotNull SelectMenu selectMenu) {
        return new Builder()
            .withIdentifier(selectMenu.getIdentifier())
            .setDisabled(selectMenu.isEnabled())
            .withPlaceholder(selectMenu.getPlaceholder())
            .withMinValues(selectMenu.getMinValues())
            .withMaxValues(selectMenu.getMaxValues())
            .withPlaceholderShowingSelectedOption(selectMenu.isPlaceholderShowingSelectedOption())
            .withOptions(selectMenu.getOptions())
            .onInteract(selectMenu.userInteraction)
            .withType(selectMenu.getMenuType());
    }

    @Override
    public @NotNull discord4j.core.object.component.SelectMenu getD4jComponent() {
        return discord4j.core.object.component.SelectMenu.of(
                this.getIdentifier(),
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
    public @NotNull Function<SelectMenuContext, Mono<Void>> getInteraction() {
        return selectMenuContext -> Mono.just(selectMenuContext)
            //.doOnNext(context -> this.updateSelected(context.getEvent().getValues()))
            .flatMap(context -> Mono.justOrEmpty(this.userInteraction)
                .flatMap(interaction -> interaction.apply(context))
                .thenReturn(context)
            )
            .filter(context -> context.getEvent().getValues().size() == 1)
            .flatMap(context -> Mono.justOrEmpty(this.getSelected().findFirst())
                .flatMap(option -> option.getInteraction().apply(OptionContext.of(context, context.getResponse(), option)))
                .switchIfEmpty(context.deferEdit())
            );
    }

    @Override
    public @NotNull Component.Type getType() {
        return this.getMenuType().getInternalType();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getIdentifier(), this.getPlaceholder(), this.getMinValues(), this.getMaxValues(), this.isPlaceholderShowingSelectedOption(), this.getOptions(), this.isDeferEdit(), this.isRequired(), this.userInteraction, this.getMenuType(), this.getSelected(), this.isEnabled());
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @Override
    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public @NotNull SelectMenu updateSelected() {
        return this.updateSelected(Concurrent.newList());
    }

    public @NotNull SelectMenu updateSelected(@NotNull String... values) {
        return this.updateSelected(Arrays.asList(values));
    }

    public @NotNull SelectMenu updateSelected(@NotNull List<String> values) {
        this.selected = values.stream()
            .map(value -> this.findOption(Option::getValue, value))
            .flatMap(Optional::stream)
            .collect(Concurrent.toUnmodifiableList());
        return this;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements ClassBuilder<SelectMenu> {

        @BuildFlag(nonNull = true)
        private String identifier;
        private boolean enabled;
        private Optional<String> placeholder = Optional.empty();
        private boolean placeholderShowingSelectedOption;
        @Range(from = 0, to = Option.MAX_ALLOWED)
        private int minValues = 1;
        @Range(from = 1, to = Option.MAX_ALLOWED)
        private int maxValues = 1;
        @BuildFlag(limit = Option.MAX_ALLOWED)
        private final ConcurrentList<Option> options = Concurrent.newList();
        private boolean deferEdit;
        private boolean required;
        private Optional<Function<SelectMenuContext, Mono<Void>>> interaction = Optional.empty();
        private Type type = Type.UNKNOWN;

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
         * Sets the interaction to execute when the {@link SelectMenu} is interacted with by a user.
         *
         * @param interaction The interaction function.
         */
        public Builder onInteract(@Nullable Function<SelectMenuContext, Mono<Void>> interaction) {
            return this.onInteract(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction to execute when the {@link SelectMenu} is interacted with by a user.
         *
         * @param interaction The interaction function.
         */
        public Builder onInteract(@NotNull Optional<Function<SelectMenuContext, Mono<Void>>> interaction) {
            this.interaction = interaction;
            return this;
        }

        /**
         * Sets the {@link SelectMenu} as disabled.
         */
        public Builder setDisabled() {
            return this.setDisabled(true);
        }

        /**
         * Sets if the {@link SelectMenu} should be disabled.
         *
         * @param value True to disable the select menu.
         */
        public Builder setDisabled(boolean value) {
            return this.setEnabled(!value);
        }

        /**
         * Sets this {@link SelectMenu} as deferred when interacting.
         */
        public Builder withDeferEdit() {
            return this.withDeferEdit(true);
        }

        /**
         * Sets whether this {@link SelectMenu} is deferred when interacting.
         *
         * @param deferEdit True to defer interaction.
         */
        public Builder withDeferEdit(boolean deferEdit) {
            this.deferEdit = deferEdit;
            return this;
        }

        /**
         * Sets the {@link SelectMenu} as enabled.
         */
        public Builder setEnabled() {
            return this.setEnabled(true);
        }

        /**
         * Sets if the {@link SelectMenu} should be enabled.
         *
         * @param value True to enable the menu.
         */
        public Builder setEnabled(boolean value) {
            this.enabled = value;
            return this;
        }

        /**
         * Sets the {@link SelectMenu} as required.
         */
        public Builder setRequired() {
            return this.setRequired(true);
        }

        /**
         * Sets if the {@link SelectMenu} should be required.
         *
         * @param value True to require the menu.
         */
        public Builder setRequired(boolean value) {
            this.required = value;
            return this;
        }

        /**
         * Overrides the default identifier of the {@link SelectMenu}.
         *
         * @param identifier The identifier to use.
         */
        public Builder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Overrides the default identifier of the {@link SelectMenu}.
         *
         * @param identifier The identifier to use.
         * @param args The objects used to format the identifier.
         */
        public Builder withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
            this.identifier = String.format(identifier, args);
            return this;
        }

        /**
         * Add {@link Option Options} to the {@link SelectMenu}.
         *
         * @param options Variable number of options to add.
         */
        public Builder withOptions(@NotNull Option... options) {
            return this.withOptions(Arrays.asList(options));
        }

        /**
         * Add {@link Option Options} {@link SelectMenu}.
         *
         * @param options Collection of options to add.
         */
        public Builder withOptions(@NotNull Iterable<Option> options) {
            options.forEach(this.options::add);
            return this;
        }

        /**
         * Sets the placeholder text to show on the {@link SelectMenu}.
         *
         * @param placeholder The placeholder text to use.
         */
        public Builder withPlaceholder(@Nullable String placeholder) {
            return this.withPlaceholder(Optional.ofNullable(placeholder));
        }

        /**
         * Sets the placeholder text to show on the {@link SelectMenu}.
         *
         * @param placeholder The placeholder text to use.
         */
        public Builder withPlaceholder(@NotNull Optional<String> placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        /**
         * Sets the minimum number of options that need to be selected for the {@link SelectMenu}.
         *
         * @param minValues The minimum number of selected {@link Option Options}.
         */
        public Builder withMinValues(int minValues) {
            this.minValues = minValues;
            return this;
        }

        /**
         * Sets the maximum number of options that need to be selected for the {@link SelectMenu}.
         *
         * @param maxValues The maximum number of selected {@link Option Options}.
         */
        public Builder withMaxValues(int maxValues) {
            this.maxValues = maxValues;
            return this;
        }

        /**
         * Sets the {@link SelectMenu} to override its placeholder with the selected {@link Option}.
         */
        public Builder withPlaceholderShowingSelectedOption() {
            return this.withPlaceholderShowingSelectedOption(true);
        }

        /**
         * Sets if the {@link SelectMenu} should override its placeholder with the selected {@link Option}.
         *
         * @param value True to override the placeholder with the selected option.
         */
        public Builder withPlaceholderShowingSelectedOption(boolean value) {
            this.placeholderShowingSelectedOption = value;
            return this;
        }

        /**
         * Sets the type of the {@link SelectMenu}.
         *
         * @param type The type to set.
         */
        public Builder withType(@NotNull Type type) {
            this.type = type;
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link SelectMenu} component.
         */
        @Override
        public @NotNull SelectMenu build() {
            Reflection.validateFlags(this);

            if (this.type == Type.UNKNOWN)
                throw new IllegalStateException("Type must be set.");

            return new SelectMenu(
                this.identifier,
                this.placeholder,
                this.minValues,
                this.maxValues,
                this.placeholderShowingSelectedOption,
                this.options,
                this.deferEdit,
                this.required,
                this.interaction,
                this.type,
                Concurrent.newUnmodifiableList(),
                this.enabled
            );
        }

    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Option {

        public static final int MAX_ALLOWED = 25;
        private static final Function<OptionContext, Mono<Void>> NOOP_HANDLER = __ -> Mono.empty();
        private final @NotNull UUID uniqueId;
        private final @NotNull String label;
        private final @NotNull String value;
        private final @NotNull Optional<String> description;
        private final @NotNull Optional<Emoji> emoji;
        private final @NotNull Function<OptionContext, Mono<Void>> interaction;

        //discord4j.core.object.component.SelectMenu.DefaultValue dv;
        // uses id: snowflake and value: EntitySelectMenu.Type#lowercase
        // TODO: See if this can be abstracted to support Snowflake/Value? (See DefaultValue in d4j)
        //  Label could be the Role name, Username, Channel name
        //  Value would be the snowflake
        //  Description would remain empty
        //  Emoji would remain empty
        //  Snowflake could be used to find an interaction

        public static @NotNull Builder builder() {
            return new Builder(UUID.randomUUID());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Option option = (Option) o;

            return Objects.equals(this.getUniqueId(), option.getUniqueId())
                && Objects.equals(this.getLabel(), option.getLabel())
                && Objects.equals(this.getValue(), option.getValue())
                && Objects.equals(this.getDescription(), option.getDescription())
                && Objects.equals(this.getEmoji(), option.getEmoji());
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
            return Objects.hash(this.getUniqueId(), this.getLabel(), this.getValue(), this.getDescription(), this.getEmoji());
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

    @Getter
    @RequiredArgsConstructor
    public enum Type {

        UNKNOWN(Component.Type.UNKNOWN),
        STRING(Component.Type.SELECT_MENU_STRING),
        USER(Component.Type.SELECT_MENU_USER),
        ROLE(Component.Type.SELECT_MENU_ROLE),
        MENTIONABLE(Component.Type.SELECT_MENU_MENTIONABLE),
        CHANNEL(Component.Type.SELECT_MENU_CHANNEL);

        private final @NotNull Component.Type internalType;

        public static @NotNull Type of(int value) {
            return Arrays.stream(values())
                .filter(type -> type.getInternalType().getValue() == value)
                .findFirst()
                .orElse(UNKNOWN);
        }

    }

}
