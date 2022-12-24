package dev.sbs.discordapi.response.page.item;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public final class NumberMenuItem<T extends Number> extends SingletonItem<T> {

    @Getter private final @NotNull Class<T> numberClass;
    @Getter private final @NotNull ConcurrentList<T> options;

    private NumberMenuItem(
        @NotNull SelectMenu.Option option,
        boolean editable,
        @NotNull Optional<T> value,
        @NotNull Class<T> numberClass,
        @NotNull ConcurrentList<T> options
    ) {
        super(option, Type.FIELD, editable, value);
        this.numberClass = numberClass;
        this.options = options;
    }

    public static <T extends Number> Builder<T> builder(@NotNull Class<T> numberClass) {
        return new Builder<>(numberClass)
            .withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public String getFieldValue(@NotNull Style itemStyle, @NotNull Column column) {
        return null; // TODO: NOT IMPLEMENTED
    }

    public Builder<T> mutate() {
        return new Builder<>(this.getNumberClass())
            .withOptions(this.getOptions())
            .withValue(this.getValue())
            .withOption(this.getOption().orElseThrow())
            .isEditable(this.isEditable());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<T extends Number> extends PageItemBuilder<NumberMenuItem<T>> {

        private final Class<T> modelClass;
        private final ConcurrentList<T> options = Concurrent.newList();
        private Optional<T> value = Optional.empty();

        public Builder<T> isEditable() {
            return this.isEditable(true);
        }

        public Builder<T> isEditable(boolean value) {
            super.editable = value;
            return this;
        }

        public Builder<T> withDescription(@Nullable String description, @NotNull Object... objects) {
            return this.withDescription(FormatUtil.formatNullable(description, objects));
        }

        public Builder<T> withDescription(@NotNull Optional<String> description) {
            super.optionBuilder.withDescription(description);
            return this;
        }

        public Builder<T> withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        public Builder<T> withEmoji(@NotNull Optional<Emoji> emoji) {
            super.optionBuilder.withEmoji(emoji);
            return this;
        }

        public Builder<T> withIdentifier(@NotNull String identifier, @NotNull Object... objects) {
            super.optionBuilder.withIdentifier(identifier, objects);
            return this;
        }

        public Builder<T> withLabel(@NotNull String label, @NotNull Object... objects) {
            super.optionBuilder.withLabel(label, objects);
            return this;
        }

        public Builder<T> withOption(@NotNull SelectMenu.Option option) {
            return this.withIdentifier(option.getIdentifier())
                .withDescription(option.getDescription())
                .withEmoji(option.getEmoji())
                .withLabel(option.getLabel())
                .withOptionValue(option.getValue());
        }

        public Builder<T> withOptionValue(@NotNull String value, @NotNull Object... objects) {
            super.optionBuilder.withValue(value, objects);
            return this;
        }

        /**
         * The options available for selection {@link NumberMenuItem}.
         *
         * @param options The options available for selection.
         */
        public Builder<T> withOptions(@NotNull T... options) {
            return this.withOptions(Arrays.asList(options));
        }

        /**
         * The options available for selection {@link NumberMenuItem}.
         *
         * @param options The options available for selection.
         */
        public Builder<T> withOptions(@NotNull Iterable<T> options) {
            this.options.clear();
            options.forEach(this.options::add);
            return this;
        }

        /**
         * Sets the selected value of the {@link NumberMenuItem}.
         *
         * @param selected The selected value of the menu item.
         */
        public Builder<T> withValue(@Nullable T selected) {
            return this.withValue(Optional.ofNullable(selected));
        }

        /**
         * Sets the selected value of the {@link NumberMenuItem}.
         *
         * @param selected The selected value of the menu item.
         */
        public Builder<T> withValue(@NotNull Optional<T> selected) {
            this.value = selected;
            return this;
        }

        @Override
        public NumberMenuItem<T> build() {
            return new NumberMenuItem<>(
                super.optionBuilder.build(),
                super.editable,
                this.value,
                this.modelClass,
                this.options
            );
        }

    }

}