package dev.sbs.discordapi.response.component.interaction.action;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.interaction.deferrable.component.action.selectmenu.OptionContext;
import dev.sbs.discordapi.context.interaction.deferrable.component.action.selectmenu.SelectMenuContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.type.InteractableComponent;
import dev.sbs.discordapi.response.component.type.PreservableComponent;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.util.exception.DiscordException;
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
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class SelectMenu extends ActionComponent implements InteractableComponent<SelectMenuContext>, PreservableComponent {

    @Getter private final @NotNull String identifier;
    @Getter private final boolean disabled;
    @Getter private final @NotNull Optional<String> placeholder;
    @Getter private final @NotNull Optional<Integer> minValue;
    @Getter private final @NotNull Optional<Integer> maxValue;
    @Getter private final boolean placeholderUsingSelectedOption;
    @Getter private final @NotNull ConcurrentList<Option> options;
    @Getter private final boolean preserved;
    @Getter private final boolean deferEdit;
    @Getter private final @NotNull PageType pageType;
    private final @NotNull ConcurrentList<Option> selected = Concurrent.newList();
    private final @NotNull Optional<Function<SelectMenuContext, Mono<Void>>> interaction;

    public static Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        SelectMenu that = (SelectMenu) o;

        return new EqualsBuilder()
            .append(this.isDisabled(), that.isDisabled())
            .append(this.isPlaceholderUsingSelectedOption(), that.isPlaceholderUsingSelectedOption())
            .append(this.isPreserved(), that.isPreserved())
            .append(this.isDeferEdit(), that.isDeferEdit())
            .append(this.getIdentifier(), that.getIdentifier())
            .append(this.getPlaceholder(), that.getPlaceholder())
            .append(this.getMinValue(), that.getMinValue())
            .append(this.getMaxValue(), that.getMaxValue())
            .append(this.getOptions(), that.getOptions())
            .append(this.getPageType(), that.getPageType())
            .append(this.getSelected(), that.getSelected())
            .build();
    }

    /**
     * Finds an existing {@link Option}.
     *
     * @param function The method reference to match with.
     * @param value The value to match with.
     * @return The matching option, if it exists.
     */
    public <S> Optional<Option> findOption(@NotNull Function<Option, S> function, S value) {
        return this.options.stream()
            .filter(option -> Objects.equals(function.apply(option), value))
            .findFirst();
    }

    public static Builder from(@NotNull SelectMenu selectMenu) {
        return new Builder()
            .withIdentifier(selectMenu.getIdentifier())
            .setDisabled(selectMenu.isDisabled())
            .withPlaceholder(selectMenu.getPlaceholder())
            .withMinValue(selectMenu.getMinValue())
            .withMaxValue(selectMenu.getMaxValue())
            .withPlaceholderUsesSelectedOption(selectMenu.isPlaceholderUsingSelectedOption())
            .withOptions(selectMenu.getOptions())
            .isPreserved(selectMenu.isDisabled());
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
            .withMinValues(this.getMinValue().orElse(1))
            .withMaxValues(this.getMaxValue().orElse(1))
            .disabled(this.isDisabled());
    }

    @Override
    public @NotNull Function<SelectMenuContext, Mono<Void>> getInteraction() {
        return selectMenuContext -> Mono.just(selectMenuContext)
            .doOnNext(context -> this.updateSelected(context.getEvent().getValues()))
            .flatMap(context -> Mono.justOrEmpty(this.interaction)
                .flatMap(interaction -> interaction.apply(context))
                .thenReturn(context)
            )
            .filter(context -> ListUtil.sizeOf(context.getEvent().getValues()) == 1)
            .flatMap(context -> Mono.justOrEmpty(this.getSelected().getFirst())
                .filter(option -> option.interaction.isPresent())
                .flatMap(option -> option.getInteraction().apply(OptionContext.of(context, context.getResponse(), option)))
                .switchIfEmpty(context.deferEdit())
            );
    }

    public @NotNull ConcurrentList<Option> getSelected() {
        return this.selected.toUnmodifiableList();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(this.getIdentifier())
            .append(this.isDisabled())
            .append(this.getPlaceholder())
            .append(this.getMinValue())
            .append(this.getMaxValue())
            .append(this.isPlaceholderUsingSelectedOption())
            .append(this.getOptions())
            .append(this.isPreserved())
            .append(this.isDeferEdit())
            .append(this.getPageType())
            .append(this.getSelected())
            .build();
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    public @NotNull SelectMenu updateSelected() {
        return this.updateSelected(Concurrent.newList());
    }

    public @NotNull SelectMenu updateSelected(@NotNull String... values) {
        return this.updateSelected(Arrays.asList(values));
    }

    public @NotNull SelectMenu updateSelected(@NotNull List<String> values) {
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
    public static final class Builder implements dev.sbs.api.util.builder.Builder<SelectMenu> {

        private String identifier;
        private boolean disabled;
        private Optional<String> placeholder = Optional.empty();
        private boolean placeholderUsingSelectedOption;
        private Optional<Integer> minValue = Optional.empty();
        private Optional<Integer> maxValue = Optional.empty();
        private final ConcurrentList<Option> options = Concurrent.newList();
        private boolean preserved;
        private boolean deferEdit;
        private PageType pageType = PageType.NONE;
        private Optional<Function<SelectMenuContext, Mono<Void>>> interaction = Optional.empty();

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
        public <S> Optional<Option> findOption(@NotNull Function<Option, S> function, S value) {
            return this.options.stream()
                .filter(option -> Objects.equals(function.apply(option), value))
                .findFirst();
        }

        /**
         * Sets this {@link SelectMenu} as preserved when a {@link Response} is removed from {@link DiscordBot#getResponseCache()}.
         */
        public Builder isPreserved() {
            return this.isPreserved(true);
        }

        /**
         * Sets whether to preserve this {@link SelectMenu} when a {@link Response} is removed from {@link DiscordBot#getResponseCache()}.
         *
         * @param preserved True to preserve this select menu.
         */
        public Builder isPreserved(boolean preserved) {
            this.preserved = preserved;
            return this;
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
         * @param disabled True to disable the select menu.
         */
        public Builder setDisabled(boolean disabled) {
            this.disabled = disabled;
            return this;
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
         * Overrides the default identifier of the {@link SelectMenu}.
         *
         * @param identifier The identifier to use.
         * @param objects The objects used to format the identifier.
         */
        public Builder withIdentifier(@NotNull String identifier, @NotNull Object... objects) {
            this.identifier = String.format(identifier, objects);
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
            if (this.options.size() == Option.MAX_ALLOWED)
                throw SimplifiedException.of(DiscordException.class)
                    .withMessage("Number of options cannot exceed %s.", Option.MAX_ALLOWED)
                    .build();

            List<Option> optionList = List.class.isAssignableFrom(options.getClass()) ? (List<Option>) options : StreamSupport.stream(options.spliterator(), false).toList();
            IntStream.range(0, Math.min(optionList.size(), (Field.MAX_ALLOWED - this.options.size()))).forEach(index -> this.options.add(optionList.get(index)));
            return this;
        }

        /**
         * Sets the page type of the {@link SelectMenu}.
         *
         * @param pageType The page type of the select menu.
         */
        public Builder withPageType(@NotNull PageType pageType) {
            this.pageType = pageType;
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
         * @param minValue The minimum number of selected {@link Option Options}.
         */
        public Builder withMinValue(@Nullable Integer minValue) {
            return this.withMinValue(Optional.ofNullable(minValue));
        }

        /**
         * Sets the minimum number of options that need to be selected for the {@link SelectMenu}.
         *
         * @param minValue The minimum number of selected {@link Option Options}.
         */
        public Builder withMinValue(@NotNull Optional<Integer> minValue) {
            this.minValue = minValue;
            return this;
        }

        /**
         * Sets the maximum number of options that need to be selected for the {@link SelectMenu}.
         *
         * @param maxValue The maximum number of selected {@link Option Options}.
         */
        public Builder withMaxValue(@Nullable Integer maxValue) {
            return this.withMaxValue(Optional.ofNullable(maxValue));
        }

        /**
         * Sets the maximum number of options that need to be selected for the {@link SelectMenu}.
         *
         * @param maxValue The maximum number of selected {@link Option Options}.
         */
        public Builder withMaxValue(@NotNull Optional<Integer> maxValue) {
            this.maxValue = maxValue;
            return this;
        }

        /**
         * Sets the {@link SelectMenu} to override it's placeholder with the selected {@link Option}.
         */
        public Builder withPlaceholderUsesSelectedOption() {
            return this.withPlaceholderUsesSelectedOption(true);
        }

        /**
         * Sets if the {@link SelectMenu} should override it's placeholder with the selected {@link Option}.
         *
         * @param value True to override placeholder with selected option.
         */
        public Builder withPlaceholderUsesSelectedOption(boolean value) {
            this.placeholderUsingSelectedOption = value;
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link SelectMenu} component.
         */
        @Override
        public SelectMenu build() {
            return new SelectMenu(
                this.identifier,
                this.disabled,
                this.placeholder,
                this.minValue,
                this.maxValue,
                this.placeholderUsingSelectedOption,
                this.options,
                this.preserved,
                this.deferEdit,
                this.pageType,
                this.interaction
            );
        }

    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Option {

        public static final int MAX_ALLOWED = 25;
        private static final Function<OptionContext, Mono<Void>> NOOP_HANDLER = __ -> Mono.empty();
        @Getter private final @NotNull UUID uniqueId;
        @Getter private final @NotNull String label;
        @Getter private final @NotNull String value;
        @Getter private final @NotNull Optional<String> description;
        @Getter private final @NotNull Optional<Emoji> emoji;
        private final @NotNull Optional<Function<OptionContext, Mono<Void>>> interaction;

        public static Builder builder() {
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

        public static Builder from(@NotNull Option option) {
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

        public discord4j.core.object.component.SelectMenu.Option getD4jOption(boolean selected) {
            discord4j.core.object.component.SelectMenu.Option d4jOption = discord4j.core.object.component.SelectMenu.Option.of(this.getLabel(), this.getValue())
                .withDescription(this.getDescription().orElse(""))
                .withDefault(selected);

            if (this.getEmoji().isPresent())
                d4jOption = d4jOption.withEmoji(this.getEmoji().get().getD4jReaction());

            return d4jOption;
        }

        public Function<OptionContext, Mono<Void>> getInteraction() {
            return this.interaction.orElse(NOOP_HANDLER);
        }

        public Builder mutate() {
            return from(this);
        }

        @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
        public static final class Builder implements dev.sbs.api.util.builder.Builder<Option> {

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
             * See {@link SelectMenu#getMinValue()} and {@link SelectMenu#getMaxValue()}.
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
             * See {@link SelectMenu#getMinValue()} and {@link SelectMenu#getMaxValue()}.
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
             * @param objects The objects used to format the description.
             */
            public Builder withDescription(@Nullable String description, @NotNull Object... objects) {
                return this.withDescription(StringUtil.formatNullable(description, objects));
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
            public Builder withLabel(@NotNull String label, @NotNull Object... objects) {
                this.label = Optional.of(String.format(label, objects));
                return this;
            }

            /**
             * Sets the value of the {@link Option}.
             *
             * @param value The option value.
             */
            public Builder withValue(@NotNull String value, @NotNull Object... objects) {
                this.value = Optional.of(String.format(value, objects));
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
                    this.interaction
                );
            }

        }
        
    }

    @RequiredArgsConstructor
    public enum PageType {

        NONE,
        PAGE,
        SUBPAGE,
        ITEM

    }

}
