package dev.sbs.discordapi.response.page.item;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.embed.Field;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Getter
public final class NumberItem<T extends Number> extends SingletonItem<T> implements SingletonFieldItem {

    private final @NotNull Class<T> numberClass;
    private final @NotNull ConcurrentList<T> options;

    private NumberItem(@NotNull SelectMenu.Option option, boolean editable, @NotNull Optional<T> value, @NotNull Class<T> numberClass, @NotNull ConcurrentList<T> options) {
        super(option, Type.FIELD, editable, value);
        this.numberClass = numberClass;
        this.options = options;
    }

    public static <T extends Number> Builder<T> builder(@NotNull Class<T> numberClass) {
        return new Builder<>(numberClass).withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public @NotNull Field getRenderField() {
        return Field.builder()
            .withName(this.getOption().getLabel())
            .withValue(
                this.getValue()
                    .map(value -> this.getNumberClass().cast(value))
                    .map(T::toString)
                    .orElse("*null*"/*getNullEmoji().asFormat()*/) // TODO
            )
            .isInline()
            .build();
    }

    public Builder<T> mutate() {
        return new Builder<>(this.getNumberClass())
            .withOptions(this.getOptions())
            .withValue(this.getValue())
            .withOption(this.getOption())
            .isEditable(this.isEditable());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<T extends Number> extends ItemBuilder<NumberItem<T>> {

        private final Class<T> modelClass;
        private final ConcurrentList<T> options = Concurrent.newList();
        private Optional<T> value = Optional.empty();

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
         * The options available for selection {@link NumberItem}.
         *
         * @param options The options available for selection.
         */
        public Builder<T> withOptions(@NotNull T... options) {
            return this.withOptions(Arrays.asList(options));
        }

        /**
         * The options available for selection {@link NumberItem}.
         *
         * @param options The options available for selection.
         */
        public Builder<T> withOptions(@NotNull Iterable<T> options) {
            this.options.clear();
            options.forEach(this.options::add);
            return this;
        }

        /**
         * Sets the selected value of the {@link NumberItem}.
         *
         * @param selected The selected value of the menu item.
         */
        public Builder<T> withValue(@Nullable T selected) {
            return this.withValue(Optional.ofNullable(selected));
        }

        /**
         * Sets the selected value of the {@link NumberItem}.
         *
         * @param selected The selected value of the menu item.
         */
        public Builder<T> withValue(@NotNull Optional<T> selected) {
            this.value = selected;
            return this;
        }

        @Override
        public @NotNull NumberItem<T> build() {
            return new NumberItem<>(
                super.optionBuilder.build(),
                super.editable,
                this.value,
                this.modelClass,
                this.options
            );
        }

    }

}