package dev.sbs.discordapi.response.menu.item.field.primitive;

import dev.sbs.api.data.model.Model;
import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class StringMenuItem extends FieldMenuItem {

    @Getter private final @NotNull ConcurrentMap<String, String> options;

    private StringMenuItem(
        @NotNull UUID uniqueId,
        @NotNull Field field,
        @NotNull ConcurrentMap<String, String> options
    ) {
        super(uniqueId, field);
        this.options = options;
    }

    public static <T extends Model> StringMenuItemBuilder builder() {
        return new StringMenuItemBuilder(UUID.randomUUID());
    }

    public StringMenuItemBuilder mutate() {
        return new StringMenuItemBuilder(this.getUniqueId())
            .withOptions(this.getOptions())
            .isInline(this.getField().isInline())
            .withName(this.getField().getName())
            .withValue(this.getField().getValue())
            .withEmoji(this.getField().getEmoji());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StringMenuItemBuilder implements Builder<StringMenuItem> {

        private final UUID uniqueId;
        private final ConcurrentMap<String, String> options = Concurrent.newMap();
        private final Field.FieldBuilder fieldBuilder = Field.builder();

        /**
         * Sets the {@link StringMenuItem} to render inline.
         */
        public StringMenuItemBuilder isInline() {
            return this.isInline(true);
        }

        /**
         * Sets whether the {@link StringMenuItem} should render inline.
         *
         * @param inline True to render menu item inline.
         */
        public StringMenuItemBuilder isInline(boolean inline) {
            this.fieldBuilder.isInline(inline);
            return this;
        }

        /**
         * Sets the emoji of the {@link StringMenuItem}.
         *
         * @param emoji The emoji of the menu item.
         */
        public StringMenuItemBuilder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the emoji of the {@link StringMenuItem}.
         *
         * @param emoji The emoji of the menu item.
         */
        public StringMenuItemBuilder withEmoji(@NotNull Optional<Emoji> emoji) {
            this.fieldBuilder.withEmoji(emoji);
            return this;
        }

        /**
         * Formats the name of the {@link StringMenuItem} with the given objects.
         *
         * @param name The name of the menu item.
         * @param objects Objects used to format the name.
         */
        public StringMenuItemBuilder withName(@NotNull String name, @NotNull Object... objects) {
            return this.withName(FormatUtil.format(name, objects));
        }

        /**
         * Sets the name of the {@link StringMenuItem}.
         *
         * @param name The name of the menu item.
         */
        public StringMenuItemBuilder withName(@Nullable String name) {
            return this.withName(Optional.ofNullable(name));
        }

        /**
         * Optionally sets the name of the {@link StringMenuItem}.
         *
         * @param name The name of the menu item.
         */
        public StringMenuItemBuilder withName(@NotNull Optional<String> name) {
            this.fieldBuilder.withName(name);
            return this;
        }

        /**
         * The options available for selection {@link StringMenuItem}.
         *
         * @param options The options available for selection.
         */
        public StringMenuItemBuilder withOptions(@NotNull Map.Entry<String, String>... options) {
            return this.withOptions(Arrays.asList(options));
        }

        /**
         * The options available for selection {@link StringMenuItem}.
         *
         * @param options The options available for selection.
         */
        public StringMenuItemBuilder withOptions(@NotNull Iterable<Map.Entry<String, String>> options) {
            this.options.clear();
            options.forEach(option -> this.options.put(option.getKey(), option.getValue()));
            return this;
        }

        /**
         * Sets the selected value of the {@link StringMenuItem}.
         *
         * @param value The selected value of the menu item.
         */
        public StringMenuItemBuilder withValue(@Nullable String value) {
            return this.withValue(Optional.ofNullable(value));
        }

        /**
         * Sets the selected value of the {@link StringMenuItem}.
         *
         * @param value The selected value of the menu item.
         */
        public StringMenuItemBuilder withValue(@NotNull Optional<String> value) {
            this.fieldBuilder.withValue(value);
            return this;
        }

        @Override
        public StringMenuItem build() {
            return new StringMenuItem(
                this.uniqueId,
                this.fieldBuilder.build(),
                this.options
            );
        }

    }

}