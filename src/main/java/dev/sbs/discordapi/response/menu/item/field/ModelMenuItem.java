package dev.sbs.discordapi.response.menu.item.field;

import dev.sbs.api.data.model.Model;
import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.discordapi.response.Emoji;
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

public final class ModelMenuItem<T extends Model> extends FieldMenuItem {

    @Getter private final @NotNull Class<T> modelClass;
    @Getter private final @NotNull ConcurrentList<T> options;
    @Getter private final @NotNull Optional<T> value;
    @Getter private final @NotNull Optional<Function<T, String>> nameFunction;
    @Getter private final @NotNull Function<T, String> valueFunction;

    private ModelMenuItem(
        @NotNull UUID uniqueId,
        @NotNull Field field,
        @NotNull Class<T> modelClass,
        @NotNull ConcurrentList<T> options,
        @NotNull Optional<T> value,
        @NotNull Optional<Function<T, String>> nameFunction,
        @NotNull Function<T, String> valueFunction
    ) {
        super(uniqueId, field);
        this.modelClass = modelClass;
        this.options = options;
        this.value = value;
        this.nameFunction = nameFunction;
        this.valueFunction = valueFunction;
    }

    public static <T extends Model> ModelMenuItemBuilder<T> builder(@NotNull Class<T> modelClass) {
        return new ModelMenuItemBuilder<>(UUID.randomUUID(), modelClass);
    }

    public ModelMenuItemBuilder<T> mutate() {
        return new ModelMenuItemBuilder<>(this.getUniqueId(), this.getModelClass())
            .withOptions(this.getOptions())
            .withValue(this.getValue())
            .withNameFunction(this.getNameFunction())
            .withValueFunction(this.getValueFunction())
            .isInline(this.getField().isInline())
            .withName(this.getField().getName())
            .withEmoji(this.getField().getEmoji());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ModelMenuItemBuilder<T extends Model> implements Builder<ModelMenuItem<T>> {

        private final UUID uniqueId;
        private final Class<T> modelClass;
        private final ConcurrentList<T> options = Concurrent.newList();
        private final Field.FieldBuilder fieldBuilder = Field.builder();
        private Optional<T> value = Optional.empty();
        private Optional<Function<T, String>> nameFunction = Optional.empty();
        private Function<T, String> valueFunction;

        /**
         * Sets the {@link ModelMenuItem} to render inline.
         */
        public ModelMenuItemBuilder<T> isInline() {
            return this.isInline(true);
        }

        /**
         * Sets whether the {@link ModelMenuItem} should render inline.
         *
         * @param inline True to render menu item inline.
         */
        public ModelMenuItemBuilder<T> isInline(boolean inline) {
            this.fieldBuilder.isInline(inline);
            return this;
        }

        /**
         * Sets the emoji of the {@link ModelMenuItem}.
         *
         * @param emoji The emoji of the menu item.
         */
        public ModelMenuItemBuilder<T> withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the emoji of the {@link ModelMenuItem}.
         *
         * @param emoji The emoji of the menu item.
         */
        public ModelMenuItemBuilder<T> withEmoji(@NotNull Optional<Emoji> emoji) {
            this.fieldBuilder.withEmoji(emoji);
            return this;
        }

        /**
         * Formats the name of the {@link ModelMenuItem} with the given objects.
         *
         * @param name The name of the menu item.
         * @param objects Objects used to format the name.
         */
        public ModelMenuItemBuilder<T> withName(@NotNull String name, @NotNull Object... objects) {
            return this.withName(FormatUtil.format(name, objects));
        }

        /**
         * Sets the name of the {@link ModelMenuItem}.
         *
         * @param name The name of the menu item.
         */
        public ModelMenuItemBuilder<T> withName(@Nullable String name) {
            return this.withName(Optional.ofNullable(name));
        }

        /**
         * Optionally sets the name of the {@link ModelMenuItem}.
         *
         * @param name The name of the menu item.
         */
        public ModelMenuItemBuilder<T> withName(@NotNull Optional<String> name) {
            this.fieldBuilder.withName(name);
            return this;
        }

        /**
         * Sets the displayed name of the {@link ModelMenuItem} value.
         *
         * @param nameFunction The display name of the menu item value.
         */
        public ModelMenuItemBuilder<T> withNameFunction(@Nullable Function<T, String> nameFunction) {
            return this.withNameFunction(Optional.ofNullable(nameFunction));
        }

        /**
         * Optionally sets the displayed name of the {@link ModelMenuItem} value.
         *
         * @param nameFunction The display name of the menu item value.
         */
        public ModelMenuItemBuilder<T> withNameFunction(@NotNull Optional<Function<T, String>> nameFunction) {
            this.nameFunction = nameFunction;
            return this;
        }

        /**
         * The options available for selection {@link ModelMenuItem}.
         *
         * @param options The options available for selection.
         */
        public ModelMenuItemBuilder<T> withOptions(@NotNull T... options) {
            return this.withOptions(Arrays.asList(options));
        }

        /**
         * The options available for selection {@link ModelMenuItem}.
         *
         * @param options The options available for selection.
         */
        public ModelMenuItemBuilder<T> withOptions(@NotNull Iterable<T> options) {
            this.options.clear();
            options.forEach(this.options::add);
            return this;
        }

        /**
         * Sets the selected value of the {@link ModelMenuItem}.
         *
         * @param selected The selected value of the menu item.
         */
        public ModelMenuItemBuilder<T> withValue(@Nullable T selected) {
            return this.withValue(Optional.ofNullable(selected));
        }

        /**
         * Sets the selected value of the {@link ModelMenuItem}.
         *
         * @param selected The selected value of the menu item.
         */
        public ModelMenuItemBuilder<T> withValue(@NotNull Optional<T> selected) {
            this.value = selected;
            return this;
        }

        /**
         * Sets the value of the {@link ModelMenuItem}.
         *
         * @param valueFunction The value of the menu item.
         */
        public ModelMenuItemBuilder<T> withValueFunction(@NotNull Function<T, String> valueFunction) {
            this.valueFunction = valueFunction;
            return this;
        }

        @Override
        public ModelMenuItem<T> build() {
            if (Objects.isNull(this.valueFunction))
                throw SimplifiedException.of(DiscordException.class)
                    .withMessage("The value function must be specified.")
                    .build();

            return new ModelMenuItem<>(
                this.uniqueId,
                this.fieldBuilder
                    .withValue(this.value.map(sel -> this.valueFunction.apply(sel)))
                    .build(),
                this.modelClass,
                this.options,
                this.value,
                this.nameFunction,
                this.valueFunction
            );
        }

    }

}