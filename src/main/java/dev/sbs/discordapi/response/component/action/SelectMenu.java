package dev.sbs.discordapi.response.component.action;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.message.interaction.component.selectmenu.OptionContext;
import dev.sbs.discordapi.context.message.interaction.component.selectmenu.SelectMenuContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class SelectMenu extends ActionComponent<SelectMenuContext, Function<SelectMenuContext, Mono<Void>>> {

    private static final Function<SelectMenuContext, Mono<Void>> NOOP_HANDLER = __ -> Mono.empty();
    @Getter private final @NotNull UUID uniqueId;
    @Getter private final boolean disabled;
    @Getter private final @NotNull Optional<String> placeholder;
    @Getter private final @NotNull Optional<Integer> minValue;
    @Getter private final @NotNull Optional<Integer> maxValue;
    @Getter private final boolean placeholderUsingSelectedOption;
    @Getter private final @NotNull ConcurrentList<Option> options;
    @Getter private final boolean preserved;
    @Getter private final boolean deferEdit;
    @Getter private final boolean pageSelector;
    private final @NotNull Optional<Function<SelectMenuContext, Mono<Void>>> selectMenuInteraction;

    public static SelectMenuBuilder builder() {
        return new SelectMenuBuilder(UUID.randomUUID());
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
            .append(this.getPlaceholder(), that.getPlaceholder())
            .append(this.getMinValue(), that.getMinValue())
            .append(this.getMaxValue(), that.getMaxValue())
            .append(this.getOptions(), that.getOptions())
            .build();
    }

    @Override
    public discord4j.core.object.component.SelectMenu getD4jComponent() {
        return discord4j.core.object.component.SelectMenu.of(
                this.getUniqueId().toString(),
                this.getOptions().stream().map(Option::getD4jOption).collect(Concurrent.toList())
            )
            .withPlaceholder(this.getPlaceholder().orElse(""))
            .withMinValues(this.getMinValue().orElse(1))
            .withMaxValues(this.getMaxValue().orElse(1))
            .disabled(this.isDisabled());
    }

    @Override
    public Function<SelectMenuContext, Mono<Void>> getInteraction() {
        return selectMenuContext -> Mono.just(selectMenuContext)
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

    private Mono<Void> handleOptionInteraction(SelectMenuContext selectMenuContext, Option option) {
        return Mono.just(selectMenuContext)
            .flatMap(context -> {
                Mono<Void> mono = Mono.empty();
                option.isDefault = true;

                // Handle Placeholder Overrides
                if (this.isPlaceholderUsingSelectedOption())
                    mono = Flux.fromIterable(this.getOptions())
                        .filter(_option -> !_option.equals(option))
                        .doOnNext(Option::setNotDefault)
                        .then();

                // Handle Interaction
                return mono.then(
                    Mono.justOrEmpty(context.getResponse())
                        .flatMap(response -> option.getInteraction()
                            .apply(OptionContext.of(selectMenuContext, response, option))
                        )
                );
            });
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(this.isDisabled())
            .append(this.getPlaceholder())
            .append(this.getMinValue())
            .append(this.getMaxValue())
            .append(this.isPlaceholderUsingSelectedOption())
            .append(this.getOptions())
            .build();
    }

    @Override
    public boolean isPaging() {
        return this.isPageSelector();
    }

    public SelectMenuBuilder mutate() {
        return new SelectMenuBuilder(this.getUniqueId())
            .setDisabled(this.isDisabled())
            .withPlaceholder(this.getPlaceholder())
            .withMinValue(this.getMinValue())
            .withMaxValue(this.getMaxValue())
            .placeholderUsesSelectedOption(this.isPlaceholderUsingSelectedOption())
            .withOptions(this.getOptions())
            .isPreserved(this.isDisabled());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class SelectMenuBuilder implements Builder<SelectMenu> {

        private final UUID uniqueId;
        private boolean disabled;
        private Optional<String> placeholder = Optional.empty();
        private boolean placeholderUsesSelectedOption;
        private Optional<Integer> minValue = Optional.empty();
        private Optional<Integer> maxValue = Optional.empty();
        private final ConcurrentList<Option> options = Concurrent.newList();
        private boolean preserved;
        private boolean deferEdit;
        private boolean pageSelector;
        private Optional<Function<SelectMenuContext, Mono<Void>>> interaction = Optional.empty();

        /**
         * Updates an existing {@link Option}.
         *
         * @param option The option to update.
         */
        public SelectMenuBuilder editOption(@NotNull Option option) {
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
         * Sets whether this {@link SelectMenu} is handled as a page selector.
         */
        public SelectMenuBuilder isPageSelector() {
            return this.isPageSelector(true);
        }

        /**
         * Sets whether this {@link SelectMenu} is handled as a page selector.
         *
         * @param pageSelector True to be used as a page selector.
         */
        public SelectMenuBuilder isPageSelector(boolean pageSelector) {
            this.pageSelector = pageSelector;
            return this;
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
         * @param interaction The interaction consumer.
         */
        public SelectMenuBuilder onInteract(@Nullable Function<SelectMenuContext, Mono<Void>> interaction) {
            return this.onInteract(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction to execute when the {@link SelectMenu} is interacted with by a user.
         *
         * @param interaction The interaction consumer.
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
         * Sets if the {@link SelectMenu} is a page selector.
         */
        public SelectMenuBuilder setPageSelector() {
            return this.setPageSelector(true);
        }

        /**
         * Sets if the {@link SelectMenu} is a page selector.
         *
         * @param pageSelector True if page selector.
         */
        public SelectMenuBuilder setPageSelector(boolean pageSelector) {
            this.pageSelector = pageSelector;
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
         * Add {@link Option Options} to the {@link SelectMenu}.
         *
         * @param options Variable number of options to add.
         */
        public SelectMenuBuilder withOptions(Option... options) {
            return this.withOptions(Arrays.asList(options));
        }

        /**
         * Add {@link Option Options} {@link SelectMenu}.
         *
         * @param options Collection of options to add.
         */
        public SelectMenuBuilder withOptions(Iterable<Option> options) {
            options.forEach(this.options::add);
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
        public SelectMenuBuilder withPlaceholder(Optional<String> placeholder) {
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
        public SelectMenuBuilder withMinValue(Optional<Integer> minValue) {
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
        public SelectMenuBuilder withMaxValue(Optional<Integer> maxValue) {
            this.maxValue = maxValue;
            return this;
        }

        /**
         * Sets the {@link SelectMenu} to override it's placeholder with the selected {@link Option}.
         */
        public SelectMenuBuilder placeholderUsesSelectedOption() {
            return this.placeholderUsesSelectedOption(true);
        }

        /**
         * Sets if the {@link SelectMenu} should override it's placeholder with the selected {@link Option}.
         *
         * @param value True to override placeholder with selected option.
         */
        public SelectMenuBuilder placeholderUsesSelectedOption(boolean value) {
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
                this.uniqueId,
                this.disabled,
                this.placeholder,
                this.minValue,
                this.maxValue,
                this.placeholderUsesSelectedOption,
                this.options,
                this.preserved,
                this.deferEdit,
                this.pageSelector,
                this.interaction
            );
        }

    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Option {

        private static final Function<OptionContext, Mono<Void>> NOOP_HANDLER = __ -> Mono.empty();
        @Getter private final @NotNull UUID uniqueId;
        @Getter private final @NotNull String label;
        @Getter private final @NotNull String value;
        @Getter private final @NotNull Optional<String> description;
        @Getter private final @NotNull Optional<Emoji> emoji;
        @Getter private boolean isDefault;
        private final @NotNull Optional<Function<OptionContext, Mono<Void>>> optionInteraction;

        public static OptionBuilder builder() {
            return new OptionBuilder(UUID.randomUUID());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Option option = (Option) o;

            return new EqualsBuilder()
                .append(this.getUniqueId(), option.getUniqueId())
                .append(this.isDefault(), option.isDefault())
                .append(this.getLabel(), option.getLabel())
                .append(this.getValue(), option.getValue())
                .append(this.getDescription(), option.getDescription())
                .append(this.getEmoji(), option.getEmoji())
                .build();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder().append(this.getLabel()).append(this.getValue()).append(this.getDescription()).append(this.getEmoji()).append(this.isDefault()).build();
        }

        public discord4j.core.object.component.SelectMenu.Option getD4jOption() {
            discord4j.core.object.component.SelectMenu.Option d4jOption = discord4j.core.object.component.SelectMenu.Option.of(this.getLabel(), this.getValue())
                .withDescription(this.getDescription().orElse(""))
                .withDefault(this.isDefault());

            if (this.getEmoji().isPresent())
                d4jOption = d4jOption.withEmoji(this.getEmoji().get().getD4jReaction());

            return d4jOption;
        }

        public Function<OptionContext, Mono<Void>> getInteraction() {
            return this.optionInteraction.orElse(NOOP_HANDLER);
        }

        public OptionBuilder mutate() {
            return new OptionBuilder(this.getUniqueId())
                .withLabel(this.getLabel())
                .withValue(this.getValue())
                .withDescription(this.getDescription())
                .withEmoji(this.getEmoji())
                .isDefault(this.isDefault())
                .onInteract(this.optionInteraction);
        }

        private void setNotDefault() {
            this.isDefault = false;
        }

        @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
        public static final class OptionBuilder implements Builder<Option> {

            private final UUID uniqueId;
            private String label;
            private String value;
            private Optional<String> description = Optional.empty();
            private Optional<Emoji> emoji = Optional.empty();
            private boolean isDefault;
            private Optional<Function<OptionContext, Mono<Void>>> interaction = Optional.empty();

            /**
             * Sets the {@link Option} as selected in the {@link SelectMenu}.
             * <br><br>
             * Only one {@link Option} per enclosing {@link SelectMenu} can be set as default.
             */
            public OptionBuilder isDefault() {
                return this.isDefault(true);
            }

            /**
             * Sets if the {@link Option} is displayed as selected in the {@link SelectMenu}.
             * <br><br>
             * Only one {@link Option} per enclosing {@link SelectMenu} can be set as default.
             *
             * @param isDefault True if the option is default selected.
             */
            public OptionBuilder isDefault(boolean isDefault) {
                this.isDefault = isDefault;
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
             */
            public OptionBuilder withDescription(@Nullable String description) {
                return this.withDescription(Optional.ofNullable(description));
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
             * Sets the label text of the {@link Option}.
             *
             * @param label The label of the option.
             */
            public OptionBuilder withLabel(@NotNull String label) {
                this.label = label;
                return this;
            }

            /**
             * Sets the value of the {@link Option}.
             *
             * @param value The option value.
             */
            public OptionBuilder withValue(@NotNull String value) {
                this.value = value;
                return this;
            }

            /**
             * Build using the configured fields.
             *
             * @return A built {@link SelectMenu} component.
             */
            @Override
            public Option build() {
                if (StringUtil.isEmpty(this.label))
                    throw SimplifiedException.of(DiscordException.class).withMessage("Option label cannot be NULL!").build();

                if (StringUtil.isEmpty(this.value))
                    throw SimplifiedException.of(DiscordException.class).withMessage("Option value cannot be NULL!").build();

                return new Option(
                    this.uniqueId,
                    this.label,
                    this.value,
                    this.description,
                    this.emoji,
                    this.isDefault,
                    this.interaction
                );
            }

        }
        
    }

}
