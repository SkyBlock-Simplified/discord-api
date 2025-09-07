package dev.sbs.discordapi.response.component.interaction.action;

import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.discordapi.context.deferrable.component.action.SelectMenuContext;
import dev.sbs.discordapi.response.component.type.LabelComponent;
import dev.sbs.discordapi.response.handler.history.TreeHistoryHandler;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
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
public final class StringSelectMenu implements SelectMenu, LabelComponent {

    private final @NotNull String userIdentifier;
    private final @NotNull Optional<String> placeholder;
    private final int minValues;
    private final int maxValues;
    private final boolean placeholderShowingSelectedOption;
    private final @NotNull ConcurrentList<Option> options;
    private final boolean deferEdit;
    @Getter(AccessLevel.NONE)
    private final @NotNull ConcurrentList<Option> selected = Concurrent.newList();
    private final @NotNull Optional<Function<SelectMenuContext, Mono<Void>>> userInteraction;
    private boolean enabled;

    public static @NotNull StringSelectMenu.StringBuilder builder() {
        return new StringBuilder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        StringSelectMenu that = (StringSelectMenu) o;

        return new EqualsBuilder()
            .append(this.isEnabled(), that.isEnabled())
            .append(this.isPlaceholderShowingSelectedOption(), that.isPlaceholderShowingSelectedOption())
            .append(this.isDeferEdit(), that.isDeferEdit())
            .append(this.getUserIdentifier(), that.getUserIdentifier())
            .append(this.getPlaceholder(), that.getPlaceholder())
            .append(this.getMinValues(), that.getMinValues())
            .append(this.getMaxValues(), that.getMaxValues())
            .append(this.getOptions(), that.getOptions())
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

    public static @NotNull StringSelectMenu.StringBuilder from(@NotNull StringSelectMenu selectMenu) {
        return new StringBuilder()
            .withIdentifier(selectMenu.getUserIdentifier())
            .setEnabled(selectMenu.isEnabled())
            .withPlaceholder(selectMenu.getPlaceholder())
            .withMinValues(selectMenu.getMinValues())
            .withMaxValues(selectMenu.getMaxValues())
            .withPlaceholderShowingSelectedOption(selectMenu.isPlaceholderShowingSelectedOption())
            .withOptions(selectMenu.getOptions())
            .onInteract(selectMenu.getUserInteraction());
    }

    public @NotNull ConcurrentList<Option> getSelected() {
        return this.selected.toUnmodifiableList();
    }

    @Override
    public @NotNull Type getType() {
        return Type.SELECT_MENU_STRING;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(this.getUserIdentifier())
            .append(this.isDisabled())
            .append(this.getPlaceholder())
            .append(this.getMinValues())
            .append(this.getMaxValues())
            .append(this.isPlaceholderShowingSelectedOption())
            .append(this.getOptions())
            .append(this.isDeferEdit())
            .append(this.getSelected())
            .build();
    }

    @Override
    public @NotNull StringBuilder mutate() {
        return from(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public @NotNull SelectMenu updateSelected() {
        return this.updateSelected(Concurrent.newList());
    }

    public @NotNull SelectMenu updateSelected(@NotNull String... values) {
        return this.updateSelected(Arrays.asList(values));
    }

    public @NotNull StringSelectMenu updateSelected(@NotNull List<String> values) {
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
    public static final class StringBuilder extends SelectMenu.Builder {

        /**
         * Updates an existing {@link Option}.
         *
         * @param option The option to update.
         */
        @Override
        public StringBuilder editOption(@NotNull Option option) {
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
         * Sets the interaction to execute when the {@link StringSelectMenu} is interacted with by a user.
         *
         * @param interaction The interaction function.
         */
        @Override
        public StringBuilder onInteract(@Nullable Function<SelectMenuContext, Mono<Void>> interaction) {
            return this.onInteract(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction to execute when the {@link StringSelectMenu} is interacted with by a user.
         *
         * @param interaction The interaction function.
         */
        @Override
        public StringBuilder onInteract(@NotNull Optional<Function<SelectMenuContext, Mono<Void>>> interaction) {
            this.interaction = interaction;
            return this;
        }

        /**
         * Sets the {@link StringSelectMenu} as disabled.
         */
        @Override
        public StringBuilder setDisabled() {
            return this.setDisabled(true);
        }

        /**
         * Sets if the {@link StringSelectMenu} should be disabled.
         *
         * @param value True to disable the select menu.
         */
        @Override
        public StringBuilder setDisabled(boolean value) {
            super.setDisabled(value);
            return this;
        }

        /**
         * Sets this {@link StringSelectMenu} as deferred when interacting.
         */
        @Override
        public StringBuilder withDeferEdit() {
            return this.withDeferEdit(true);
        }

        /**
         * Sets whether this {@link StringSelectMenu} is deferred when interacting.
         *
         * @param deferEdit True to defer interaction.
         */
        @Override
        public StringBuilder withDeferEdit(boolean deferEdit) {
            this.deferEdit = deferEdit;
            return this;
        }

        /**
         * Sets the {@link StringSelectMenu} as enabled.
         */
        @Override
        public StringBuilder setEnabled() {
            return this.setEnabled(true);
        }

        /**
         * Sets if the {@link StringSelectMenu} should be enabled.
         *
         * @param value True to enable the button.
         */
        @Override
        public StringBuilder setEnabled(boolean value) {
            super.setEnabled(value);
            return this;
        }

        /**
         * Overrides the default identifier of the {@link StringSelectMenu}.
         *
         * @param identifier The identifier to use.
         */
        @Override
        public StringBuilder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Overrides the default identifier of the {@link StringSelectMenu}.
         *
         * @param identifier The identifier to use.
         * @param args The objects used to format the identifier.
         */
        @Override
        public StringBuilder withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
            this.identifier = String.format(identifier, args);
            return this;
        }

        /**
         * Add {@link Option Options} to the {@link StringSelectMenu}.
         *
         * @param options Variable number of options to add.
         */
        @Override
        public StringBuilder withOptions(@NotNull Option... options) {
            return this.withOptions(Arrays.asList(options));
        }

        /**
         * Add {@link Option Options} {@link StringSelectMenu}.
         *
         * @param options Collection of options to add.
         */
        @Override
        public StringBuilder withOptions(@NotNull Iterable<Option> options) {
            options.forEach(this.options::add);
            return this;
        }

        /**
         * Sets the placeholder text to show on the {@link StringSelectMenu}.
         *
         * @param placeholder The placeholder text to use.
         */
        @Override
        public StringBuilder withPlaceholder(@Nullable String placeholder) {
            return this.withPlaceholder(Optional.ofNullable(placeholder));
        }

        /**
         * Sets the placeholder text to show on the {@link StringSelectMenu}.
         *
         * @param placeholder The placeholder text to use.
         */
        @Override
        public StringBuilder withPlaceholder(@NotNull Optional<String> placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        /**
         * Sets the minimum number of options that need to be selected for the {@link StringSelectMenu}.
         *
         * @param minValues The minimum number of selected {@link Option Options}.
         */
        @Override
        public StringBuilder withMinValues(int minValues) {
            this.minValues = minValues;
            return this;
        }

        /**
         * Sets the maximum number of options that need to be selected for the {@link StringSelectMenu}.
         *
         * @param maxValues The maximum number of selected {@link Option Options}.
         */
        @Override
        public StringBuilder withMaxValues(int maxValues) {
            this.maxValues = maxValues;
            return this;
        }

        /**
         * Sets the {@link StringSelectMenu} to override its placeholder with the selected {@link Option}.
         */
        @Override
        public StringBuilder withPlaceholderShowingSelectedOption() {
            return this.withPlaceholderShowingSelectedOption(true);
        }

        /**
         * Sets if the {@link StringSelectMenu} should override its placeholder with the selected {@link Option}.
         *
         * @param value True to override the placeholder with the selected option.
         */
        @Override
        public StringBuilder withPlaceholderShowingSelectedOption(boolean value) {
            this.placeholderShowingSelectedOption = value;
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link StringSelectMenu} component.
         */
        @Override
        public @NotNull StringSelectMenu build() {
            Reflection.validateFlags(this);

            return new StringSelectMenu(
                this.identifier,
                this.placeholder,
                this.minValues,
                this.maxValues,
                this.placeholderShowingSelectedOption,
                this.options,
                this.deferEdit,
                this.interaction,
                this.enabled
            );
        }

    }

    @Getter
    @RequiredArgsConstructor
    @SuppressWarnings("unchecked")
    public enum PageType {

        NONE(__ -> Mono.empty()),
        PAGE_SELECTOR(context -> context.consumeResponse(response -> {
            String selectedValue = context.getSelected().getFirst().orElseThrow().getValue();
            response.getHistoryHandler().gotoTopLevelPage(selectedValue);
        })),
        SUBPAGE_SELECTOR(context -> context.consumeResponse(response -> {
            String selectedValue = context.getSelected().getFirst().orElseThrow().getValue();

            if (selectedValue.equals("BACK"))
                response.getHistoryHandler().gotoPreviousPage();
            else
                ((TreeHistoryHandler<?, String>) response.getHistoryHandler()).gotoSubPage(selectedValue);
        })),
        ITEM(context -> context.consumeResponse(response -> {
            /*
             TODO
                Build a viewer that converts the item list
                into something that either lists data about an item
                or a sub-list from PageItem

             TODO
                Viewer will need the ability to enable editing
                and code to do something on save
             */
        }));

        private final @NotNull Function<SelectMenuContext, Mono<Void>> interaction;

    }

}
