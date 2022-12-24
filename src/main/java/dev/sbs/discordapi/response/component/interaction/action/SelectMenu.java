package dev.sbs.discordapi.response.component.interaction.action;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.ListUtil;
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
import reactor.core.publisher.Flux;
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

    private static final Function<SelectMenuContext, Mono<Void>> NOOP_HANDLER = __ -> Mono.empty();
    @Getter private final @NotNull String identifier;
    @Getter private final boolean disabled;
    @Getter private final @NotNull Optional<String> placeholder;
    @Getter private final @NotNull Optional<Integer> minValue;
    @Getter private final @NotNull Optional<Integer> maxValue;
    @Getter private final boolean placeholderUsingSelectedOption;
    @Getter private final @NotNull ConcurrentList<Option> options;
    @Getter private final boolean preserved;
    @Getter private final boolean deferEdit;
    @Getter private final PageType pageType;
    private final ConcurrentList<Option> selected = Concurrent.newList();
    private final @NotNull Optional<Function<SelectMenuContext, Mono<Void>>> selectMenuInteraction;

    public static SelectMenuBuilder builder() {
        return new SelectMenuBuilder().withIdentifier(UUID.randomUUID().toString());
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

    public static SelectMenuBuilder from(@NotNull SelectMenu selectMenu) {
        return new SelectMenuBuilder()
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
    public discord4j.core.object.component.SelectMenu getD4jComponent() {
        return discord4j.core.object.component.SelectMenu.of(
                this.getIdentifier(),
                this.getOptions().stream().map(Option::getD4jOption).collect(Concurrent.toList())
            )
            .withPlaceholder(this.getPlaceholder().orElse(""))
            .withMinValues(this.getMinValue().orElse(1))
            .withMaxValues(this.getMaxValue().orElse(1))
            .disabled(this.isDisabled());
    }

    @Override
    public @NotNull Function<SelectMenuContext, Mono<Void>> getInteraction() {
        return selectMenuContext -> Mono.just(selectMenuContext)
            .doOnNext(context -> this.updateSelected(context.getValues()))
            .flatMap(context -> Mono.justOrEmpty(this.selectMenuInteraction)
                .flatMap(interaction -> interaction.apply(context))
                .thenReturn(context)
            )
            .filter(context -> ListUtil.sizeOf(context.getEvent().getValues()) == 1)
            .flatMap(context -> Flux.fromIterable(this.getOptions())
                .filter(option -> option.getValue().equals(context.getEvent().getValues().get(0)))
                .singleOrEmpty()
                .filter(option -> option.optionInteraction.isPresent())
                .flatMap(option -> this.handleOptionInteraction(context, option).thenReturn(context))
                .switchIfEmpty(selectMenuContext.deferEdit().thenReturn(selectMenuContext))
            )
            .then();
    }

    public @NotNull Function<SelectMenuContext, Mono<SelectMenuContext>> getPlaceholderUpdate() {
        return selectMenuContext -> Flux.fromIterable(this.getOptions())
            .filter(option -> option.getValue().equals(selectMenuContext.getEvent().getValues().get(0)))
            .singleOrEmpty()
            .flatMap(option -> {
                Mono<Void> mono = Mono.empty();

                if (this.isPlaceholderUsingSelectedOption()) {
                    option.placeholderSelected = true;

                    mono = Flux.fromIterable(this.getOptions())
                        .filter(_option -> !_option.equals(option))
                        .doOnNext(Option::setNotSelected)
                        .then();
                }

                return mono.thenReturn(selectMenuContext);
            });
    }

    public @NotNull ConcurrentList<Option> getSelected() {
        return Concurrent.newUnmodifiableList(this.selected);
    }

    private @NotNull Mono<Void> handleOptionInteraction(SelectMenuContext selectMenuContext, Option option) {
        return Mono.just(selectMenuContext).flatMap(context -> this.getPlaceholderUpdate()
            .apply(selectMenuContext)
            .then(
                Mono.justOrEmpty(context.getResponse())
                    .flatMap(response -> option.getInteraction()
                        .apply(OptionContext.of(selectMenuContext, response, option))
                    )
            ));
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

    @Override
    public boolean isPaging() {
        return this.getPageType() != PageType.NONE;
    }

    public @NotNull SelectMenuBuilder mutate() {
        return from(this);
    }

    public void updateSelected(@NotNull List<String> values) {
        this.selected.clear();
        this.selected.addAll(
            values.stream()
                .map(value -> this.findOption(Option::getValue, value))
                .flatMap(Optional::stream)
                .collect(Concurrent.toList())
        );
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class SelectMenuBuilder implements Builder<SelectMenu> {

        private String identifier;
        private boolean disabled;
        private Optional<String> placeholder = Optional.empty();
        private boolean placeholderUsesSelectedOption;
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
        public SelectMenuBuilder editOption(@NotNull Option option) {
            this.options.stream()
                .filter(innerOption -> innerOption.getIdentifier().equals(option.getIdentifier()))
                .findFirst()
                .ifPresent(innerOption -> {
                    int index = this.options.indexOf(innerOption);
                    this.options.remove(index);
                    this.options.add(index, option);
                });
            return this;
        }

        public SelectMenuBuilder clearSelectOption() {
            this.options.forEach(Option::setNotSelected);
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
        public SelectMenuBuilder isPreserved() {
            return this.isPreserved(true);
        }

        /**
         * Sets whether to preserve this {@link SelectMenu} when a {@link Response} is removed from {@link DiscordBot#getResponseCache()}.
         *
         * @param preserved True to preserve this select menu.
         */
        public SelectMenuBuilder isPreserved(boolean preserved) {
            this.preserved = preserved;
            return this;
        }

        /**
         * Sets the interaction to execute when the {@link SelectMenu} is interacted with by a user.
         *
         * @param interaction The interaction function.
         */
        public SelectMenuBuilder onInteract(@Nullable Function<SelectMenuContext, Mono<Void>> interaction) {
            return this.onInteract(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction to execute when the {@link SelectMenu} is interacted with by a user.
         *
         * @param interaction The interaction function.
         */
        public SelectMenuBuilder onInteract(@NotNull Optional<Function<SelectMenuContext, Mono<Void>>> interaction) {
            this.interaction = interaction;
            return this;
        }

        /**
         * Sets the {@link SelectMenu} as disabled.
         */
        public SelectMenuBuilder setDisabled() {
            return this.setDisabled(true);
        }

        /**
         * Sets if the {@link SelectMenu} should be disabled.
         *
         * @param disabled True to disable the select menu.
         */
        public SelectMenuBuilder setDisabled(boolean disabled) {
            this.disabled = disabled;
            return this;
        }

        /**
         * Sets this {@link SelectMenu} as deferred when interacting.
         */
        public SelectMenuBuilder withDeferEdit() {
            return this.withDeferEdit(true);
        }

        /**
         * Sets whether this {@link SelectMenu} is deferred when interacting.
         *
         * @param deferEdit True to defer interaction.
         */
        public SelectMenuBuilder withDeferEdit(boolean deferEdit) {
            this.deferEdit = deferEdit;
            return this;
        }

        /**
         * Overrides the default identifier of the {@link SelectMenu}.
         *
         * @param identifier The identifier to use.
         * @param objects The objects used to format the identifier.
         */
        public SelectMenuBuilder withIdentifier(@NotNull String identifier, @NotNull Object... objects) {
            this.identifier = FormatUtil.format(identifier, objects);
            return this;
        }

        /**
         * Add {@link Option Options} to the {@link SelectMenu}.
         *
         * @param options Variable number of options to add.
         */
        public SelectMenuBuilder withOptions(@NotNull Option... options) {
            return this.withOptions(Arrays.asList(options));
        }

        /**
         * Add {@link Option Options} {@link SelectMenu}.
         *
         * @param options Collection of options to add.
         */
        public SelectMenuBuilder withOptions(@NotNull Iterable<Option> options) {
            if (this.options.size() == Option.MAX_ALLOWED)
                throw SimplifiedException.of(DiscordException.class)
                    .withMessage("Number of options cannot exceed {0}!", Option.MAX_ALLOWED)
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
        public SelectMenuBuilder withPageType(@NotNull PageType pageType) {
            this.pageType = pageType;
            return this;
        }

        /**
         * Sets the placeholder text to show on the {@link SelectMenu}.
         *
         * @param placeholder The placeholder text to use.
         */
        public SelectMenuBuilder withPlaceholder(@Nullable String placeholder) {
            return this.withPlaceholder(Optional.ofNullable(placeholder));
        }

        /**
         * Sets the placeholder text to show on the {@link SelectMenu}.
         *
         * @param placeholder The placeholder text to use.
         */
        public SelectMenuBuilder withPlaceholder(@NotNull Optional<String> placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        /**
         * Sets the minimum number of options that need to be selected for the {@link SelectMenu}.
         *
         * @param minValue The minimum number of selected {@link Option Options}.
         */
        public SelectMenuBuilder withMinValue(@Nullable Integer minValue) {
            return this.withMinValue(Optional.ofNullable(minValue));
        }

        /**
         * Sets the minimum number of options that need to be selected for the {@link SelectMenu}.
         *
         * @param minValue The minimum number of selected {@link Option Options}.
         */
        public SelectMenuBuilder withMinValue(@NotNull Optional<Integer> minValue) {
            this.minValue = minValue;
            return this;
        }

        /**
         * Sets the maximum number of options that need to be selected for the {@link SelectMenu}.
         *
         * @param maxValue The maximum number of selected {@link Option Options}.
         */
        public SelectMenuBuilder withMaxValue(@Nullable Integer maxValue) {
            return this.withMaxValue(Optional.ofNullable(maxValue));
        }

        /**
         * Sets the maximum number of options that need to be selected for the {@link SelectMenu}.
         *
         * @param maxValue The maximum number of selected {@link Option Options}.
         */
        public SelectMenuBuilder withMaxValue(@NotNull Optional<Integer> maxValue) {
            this.maxValue = maxValue;
            return this;
        }

        /**
         * Sets the {@link SelectMenu} to override it's placeholder with the selected {@link Option}.
         */
        public SelectMenuBuilder withPlaceholderUsesSelectedOption() {
            return this.withPlaceholderUsesSelectedOption(true);
        }

        /**
         * Sets if the {@link SelectMenu} should override it's placeholder with the selected {@link Option}.
         *
         * @param value True to override placeholder with selected option.
         */
        public SelectMenuBuilder withPlaceholderUsesSelectedOption(boolean value) {
            this.placeholderUsesSelectedOption = value;
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
                this.placeholderUsesSelectedOption,
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
        @Getter private final @NotNull String identifier;
        @Getter private final @NotNull String label;
        @Getter private final @NotNull String value;
        @Getter private final @NotNull Optional<String> description;
        @Getter private final @NotNull Optional<Emoji> emoji;
        @Getter private boolean placeholderSelected;
        private final @NotNull Optional<Function<OptionContext, Mono<Void>>> optionInteraction;

        public static OptionBuilder builder() {
            return new OptionBuilder().withIdentifier(UUID.randomUUID().toString());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Option option = (Option) o;

            return new EqualsBuilder()
                .append(this.isPlaceholderSelected(), option.isPlaceholderSelected())
                .append(this.getIdentifier(), option.getIdentifier())
                .append(this.getLabel(), option.getLabel())
                .append(this.getValue(), option.getValue())
                .append(this.getDescription(), option.getDescription())
                .append(this.getEmoji(), option.getEmoji())
                .build();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                .append(this.getIdentifier())
                .append(this.getLabel())
                .append(this.getValue())
                .append(this.getDescription())
                .append(this.getEmoji())
                .append(this.isPlaceholderSelected())
                .build();
        }

        public discord4j.core.object.component.SelectMenu.Option getD4jOption() {
            discord4j.core.object.component.SelectMenu.Option d4jOption = discord4j.core.object.component.SelectMenu.Option.of(this.getLabel(), this.getValue())
                .withDescription(this.getDescription().orElse(""))
                .withDefault(this.isPlaceholderSelected());

            if (this.getEmoji().isPresent())
                d4jOption = d4jOption.withEmoji(this.getEmoji().get().getD4jReaction());

            return d4jOption;
        }

        public Function<OptionContext, Mono<Void>> getInteraction() {
            return this.optionInteraction.orElse(NOOP_HANDLER);
        }

        public OptionBuilder mutate() {
            return new OptionBuilder()
                .withIdentifier(this.getIdentifier())
                .withLabel(this.getLabel())
                .withValue(this.getValue())
                .withDescription(this.getDescription())
                .withEmoji(this.getEmoji())
                .isPlaceholderSelected(this.isPlaceholderSelected())
                .onInteract(this.optionInteraction);
        }

        private void setNotSelected() {
            this.placeholderSelected = false;
        }

        @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
        public static final class OptionBuilder implements Builder<Option> {

            private String identifier;
            private Optional<String> label = Optional.empty();
            private Optional<String> value = Optional.empty();
            private Optional<String> description = Optional.empty();
            private Optional<Emoji> emoji = Optional.empty();
            private boolean placeholderSelected;
            private Optional<Function<OptionContext, Mono<Void>>> interaction = Optional.empty();

            /**
             * Sets the {@link Option} as selected in the {@link SelectMenu}.
             * <br><br>
             * Only one {@link Option} per enclosing {@link SelectMenu} can be set as default.
             */
            public OptionBuilder isPlaceholderSelected() {
                return this.isPlaceholderSelected(true);
            }

            /**
             * Sets if the {@link Option} is displayed as selected in the {@link SelectMenu}.
             * <br><br>
             * Only one {@link Option} per enclosing {@link SelectMenu} can be set as default.
             *
             * @param isDefault True if the option is default selected.
             */
            public OptionBuilder isPlaceholderSelected(boolean isDefault) {
                this.placeholderSelected = isDefault;
                return this;
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
            public OptionBuilder onInteract(@Nullable Function<OptionContext, Mono<Void>> interaction) {
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
            public OptionBuilder onInteract(@NotNull Optional<Function<OptionContext, Mono<Void>>> interaction) {
                this.interaction = interaction;
                return this;
            }

            /**
             * Sets the description of the {@link Option} shown under the label.
             *
             * @param description The description of the option.
             * @param objects The objects used to format the description.
             */
            public OptionBuilder withDescription(@Nullable String description, @NotNull Object... objects) {
                return this.withDescription(FormatUtil.formatNullable(description, objects));
            }

            /**
             * Sets the description of the {@link Option} shown under the label.
             *
             * @param description The description of the option.
             */
            public OptionBuilder withDescription(@NotNull Optional<String> description) {
                this.description = description;
                return this;
            }

            /**
             * Sets the {@link Emoji} shown to the left of the label.
             *
             * @param emoji The emoji of the option.
             */
            public OptionBuilder withEmoji(@Nullable Emoji emoji) {
                return this.withEmoji(Optional.ofNullable(emoji));
            }

            /**
             * Sets the {@link Emoji} shown to the left of the label.
             *
             * @param emoji The emoji of the option.
             */
            public OptionBuilder withEmoji(@NotNull Optional<Emoji> emoji) {
                this.emoji = emoji;
                return this;
            }

            /**
             * Overrides the default identifier of the {@link SelectMenu}.
             *
             * @param identifier The identifier to use.
             * @param objects The objects used to format the identifier.
             */
            public OptionBuilder withIdentifier(@NotNull String identifier, @NotNull Object... objects) {
                this.identifier = FormatUtil.format(identifier, objects);
                return this;
            }

            /**
             * Sets the label text of the {@link Option}.
             *
             * @param label The label of the option.
             */
            public OptionBuilder withLabel(@NotNull String label, @NotNull Object... objects) {
                this.label = Optional.of(FormatUtil.format(label, objects));
                return this;
            }

            /**
             * Sets the value of the {@link Option}.
             *
             * @param value The option value.
             */
            public OptionBuilder withValue(@NotNull String value, @NotNull Object... objects) {
                this.value = Optional.of(FormatUtil.format(value, objects));
                return this;
            }

            /**
             * Build using the configured fields.
             *
             * @return A built {@link SelectMenu} component.
             */
            @Override
            public Option build() {
                return new Option(
                    this.identifier,
                    this.label.orElse(this.identifier),
                    this.value.orElse(this.identifier),
                    this.description,
                    this.emoji,
                    this.placeholderSelected,
                    this.interaction
                );
            }

        }
        
    }

    @RequiredArgsConstructor
    public enum PageType {

        NONE,
        PAGE,
        SUBPAGE

    }

}
