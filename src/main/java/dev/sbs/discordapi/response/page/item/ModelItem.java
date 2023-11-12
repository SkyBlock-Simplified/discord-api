package dev.sbs.discordapi.response.page.item;

import dev.sbs.api.data.model.Model;
import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.util.exception.DiscordException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public final class ModelItem<T extends Model> extends SingletonItem<T> implements SingletonFieldItem {

    @Getter private final @NotNull Class<T> modelClass;
    @Getter private final @NotNull ConcurrentList<T> options;
    @Getter private final @NotNull Optional<Function<T, String>> nameFunction;
    @Getter private final @NotNull Function<T, String> valueFunction;

    private ModelItem(
        @NotNull SelectMenu.Option option,
        boolean editable,
        @NotNull Optional<T> value,
        @NotNull Class<T> modelClass,
        @NotNull ConcurrentList<T> options,
        @NotNull Optional<Function<T, String>> nameFunction,
        @NotNull Function<T, String> valueFunction
    ) {
        super(option, Type.FIELD, editable, value);
        this.modelClass = modelClass;
        this.options = options;
        this.nameFunction = nameFunction;
        this.valueFunction = valueFunction;
    }

    public static <T extends Model> Builder<T> builder(@NotNull Class<T> modelClass) {
        return new Builder<>(modelClass).withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public Field getRenderField() {
        return Field.builder()
            .withName(
                this.getValue()
                    .flatMap(model -> this.getNameFunction().map(nameFunction -> nameFunction.apply(model)))
                    .orElse(this.getOption().getLabel())
            )
            .withValue(
                this.getValue()
                    .map(this.getValueFunction())
                    .orElse("*null*"/*getNullEmoji().asFormat()*/) // TODO
            )
            .isInline()
            .build();
    }

    public Builder<T> mutate() {
        return new Builder<>(this.getModelClass())
            .withOptions(this.getOptions())
            .withValue(this.getValue())
            .withNameFunction(this.getNameFunction())
            .withValueFunction(this.getValueFunction())
            .withOption(this.getOption())
            .isEditable(this.isEditable());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<T extends Model> extends ItemBuilder<ModelItem<T>> {

        private final Class<T> modelClass;
        private final ConcurrentList<T> options = Concurrent.newList();
        private Optional<T> value = Optional.empty();
        private Optional<Function<T, String>> nameFunction = Optional.empty();
        private Function<T, String> valueFunction;

        @Override
        public Builder<T> isEditable() {
            return this.isEditable(true);
        }

        @Override
        public Builder<T> isEditable(boolean value) {
            super.editable = value;
            return this;
        }

        @Override
        public Builder<T> withDescription(@Nullable String description, @NotNull Object... objects) {
            return this.withDescription(StringUtil.formatNullable(description, objects));
        }

        @Override
        public Builder<T> withDescription(@NotNull Optional<String> description) {
            super.optionBuilder.withDescription(description);
            return this;
        }

        @Override
        public Builder<T> withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        @Override
        public Builder<T> withEmoji(@NotNull Optional<Emoji> emoji) {
            super.optionBuilder.withEmoji(emoji);
            return this;
        }

        @Override
        public Builder<T> withIdentifier(@NotNull String identifier, @NotNull Object... objects) {
            super.optionBuilder.withValue(identifier, objects);
            return this;
        }

        @Override
        public Builder<T> withLabel(@NotNull String label, @NotNull Object... objects) {
            super.optionBuilder.withLabel(label, objects);
            return this;
        }

        public Builder<T> withOption(@NotNull SelectMenu.Option option) {
            return this.withIdentifier(option.getValue())
                .withDescription(option.getDescription())
                .withEmoji(option.getEmoji())
                .withLabel(option.getLabel());
        }

        /**
         * Sets the displayed name of the {@link ModelItem} value.
         *
         * @param nameFunction The display name of the menu item value.
         */
        public Builder<T> withNameFunction(@Nullable Function<T, String> nameFunction) {
            return this.withNameFunction(Optional.ofNullable(nameFunction));
        }

        /**
         * Optionally sets the displayed name of the {@link ModelItem} value.
         *
         * @param nameFunction The display name of the menu item value.
         */
        public Builder<T> withNameFunction(@NotNull Optional<Function<T, String>> nameFunction) {
            this.nameFunction = nameFunction;
            return this;
        }

        /**
         * The options available for selection {@link ModelItem}.
         *
         * @param options The options available for selection.
         */
        public Builder<T> withOptions(@NotNull T... options) {
            return this.withOptions(Arrays.asList(options));
        }

        /**
         * The options available for selection {@link ModelItem}.
         *
         * @param options The options available for selection.
         */
        public Builder<T> withOptions(@NotNull Iterable<T> options) {
            this.options.clear();
            options.forEach(this.options::add);
            return this;
        }

        /**
         * Sets the selected value of the {@link ModelItem}.
         *
         * @param selected The selected value of the menu item.
         */
        public Builder<T> withValue(@Nullable T selected) {
            return this.withValue(Optional.ofNullable(selected));
        }

        /**
         * Sets the selected value of the {@link ModelItem}.
         *
         * @param selected The selected value of the menu item.
         */
        public Builder<T> withValue(@NotNull Optional<T> selected) {
            this.value = selected;
            return this;
        }

        /**
         * Sets the value of the {@link ModelItem}.
         *
         * @param valueFunction The value of the menu item.
         */
        public Builder<T> withValueFunction(@NotNull Function<T, String> valueFunction) {
            this.valueFunction = valueFunction;
            return this;
        }

        @Override
        public ModelItem<T> build() {
            if (Objects.isNull(this.valueFunction))
                throw SimplifiedException.of(DiscordException.class)
                    .withMessage("The value function must be specified.")
                    .build();

            return new ModelItem<>(
                super.optionBuilder.build(),
                super.editable,
                this.value,
                this.modelClass,
                this.options,
                this.nameFunction,
                this.valueFunction
            );
        }

    }

}