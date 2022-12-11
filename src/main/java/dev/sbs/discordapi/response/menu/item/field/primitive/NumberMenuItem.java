package dev.sbs.discordapi.response.menu.item.field.primitive;

import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.response.menu.item.field.FieldMenuItem;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public final class NumberMenuItem<T extends Number> extends FieldMenuItem {

    @Getter private final @NotNull Class<T> numberClass;
    @Getter private final @NotNull ConcurrentList<T> options;
    @Getter private final @NotNull Optional<T> value;

    private NumberMenuItem(
        @NotNull UUID uniqueId,
        @NotNull Field field,
        @NotNull Class<T> numberClass,
        @NotNull ConcurrentList<T> options,
        @NotNull Optional<T> value
    ) {
        super(uniqueId, field);
        this.numberClass = numberClass;
        this.options = options;
        this.value = value;
    }

    public static <T extends Number> NumberMenuItemBuilder<T> builder(@NotNull Class<T> numberClass) {
        return new NumberMenuItemBuilder<>(UUID.randomUUID(), numberClass);
    }

    public NumberMenuItemBuilder<T> mutate() {
        return new NumberMenuItemBuilder<>(this.getUniqueId(), this.getNumberClass())
            .withOptions(this.getOptions())
            .withValue(this.getValue())
            .isInline(this.getField().isInline())
            .withName(this.getField().getName())
            .withEmoji(this.getField().getEmoji());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class NumberMenuItemBuilder<T extends Number> implements Builder<NumberMenuItem<T>> {

        private final UUID uniqueId;
        private final Class<T> modelClass;
        private final ConcurrentList<T> options = Concurrent.newList();
        private final Field.FieldBuilder fieldBuilder = Field.builder();
        private Optional<T> value = Optional.empty();

        /**
         * Sets the {@link NumberMenuItem} to render inline.
         */
        public NumberMenuItemBuilder<T> isInline() {
            return this.isInline(true);
        }

        /**
         * Sets whether the {@link NumberMenuItem} should render inline.
         *
         * @param inline True to render menu item inline.
         */
        public NumberMenuItemBuilder<T> isInline(boolean inline) {
            this.fieldBuilder.isInline(inline);
            return this;
        }

        /**
         * Sets the emoji of the {@link NumberMenuItem}.
         *
         * @param emoji The emoji of the menu item.
         */
        public NumberMenuItemBuilder<T> withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the emoji of the {@link NumberMenuItem}.
         *
         * @param emoji The emoji of the menu item.
         */
        public NumberMenuItemBuilder<T> withEmoji(@NotNull Optional<Emoji> emoji) {
            this.fieldBuilder.withEmoji(emoji);
            return this;
        }

        /**
         * Formats the name of the {@link NumberMenuItem} with the given objects.
         *
         * @param name The name of the menu item.
         * @param objects Objects used to format the name.
         */
        public NumberMenuItemBuilder<T> withName(@NotNull String name, @NotNull Object... objects) {
            return this.withName(FormatUtil.format(name, objects));
        }

        /**
         * Sets the name of the {@link NumberMenuItem}.
         *
         * @param name The name of the menu item.
         */
        public NumberMenuItemBuilder<T> withName(@Nullable String name) {
            return this.withName(Optional.ofNullable(name));
        }

        /**
         * Optionally sets the name of the {@link NumberMenuItem}.
         *
         * @param name The name of the menu item.
         */
        public NumberMenuItemBuilder<T> withName(@NotNull Optional<String> name) {
            this.fieldBuilder.withName(name);
            return this;
        }

        /**
         * The options available for selection {@link NumberMenuItem}.
         *
         * @param options The options available for selection.
         */
        public NumberMenuItemBuilder<T> withOptions(@NotNull T... options) {
            return this.withOptions(Arrays.asList(options));
        }

        /**
         * The options available for selection {@link NumberMenuItem}.
         *
         * @param options The options available for selection.
         */
        public NumberMenuItemBuilder<T> withOptions(@NotNull Iterable<T> options) {
            this.options.clear();
            options.forEach(this.options::add);
            return this;
        }

        /**
         * Sets the selected value of the {@link NumberMenuItem}.
         *
         * @param selected The selected value of the menu item.
         */
        public NumberMenuItemBuilder<T> withValue(@Nullable T selected) {
            return this.withValue(Optional.ofNullable(selected));
        }

        /**
         * Sets the selected value of the {@link NumberMenuItem}.
         *
         * @param selected The selected value of the menu item.
         */
        public NumberMenuItemBuilder<T> withValue(@NotNull Optional<T> selected) {
            this.value = selected;
            return this;
        }

        @Override
        public NumberMenuItem<T> build() {
            return new NumberMenuItem<>(
                this.uniqueId,
                this.fieldBuilder
                    .withValue(this.value.map(String::valueOf))
                    .build(),
                this.modelClass,
                this.options,
                this.value
            );
        }

    }

}