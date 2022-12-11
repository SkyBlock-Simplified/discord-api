package dev.sbs.discordapi.response.menu.item.field;

import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.embed.Field;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public final class ToggleMenuItem extends FieldMenuItem {

    @Getter private final Boolean value;

    private ToggleMenuItem(
        @NotNull UUID uniqueId,
        @NotNull Field field,
        boolean value
    ) {
        super(uniqueId, field);
        this.value = value;
    }

    public static ToggleMenuItemBuilder builder() {
        return new ToggleMenuItemBuilder(UUID.randomUUID());
    }

    public ToggleMenuItemBuilder mutate() {
        return new ToggleMenuItemBuilder(this.getUniqueId())
            .withValue(this.getValue())
            .isInline(this.getField().isInline())
            .withName(this.getField().getName())
            .withEmoji(this.getField().getEmoji());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ToggleMenuItemBuilder implements Builder<ToggleMenuItem> {

        private final UUID uniqueId;
        private final Field.FieldBuilder fieldBuilder = Field.builder();
        private boolean value;

        /**
         * Sets the {@link ToggleMenuItem} to render inline.
         */
        public ToggleMenuItemBuilder isInline() {
            return this.isInline(true);
        }

        /**
         * Sets whether the {@link ToggleMenuItem} should render inline.
         *
         * @param inline True to render menu item inline.
         */
        public ToggleMenuItemBuilder isInline(boolean inline) {
            this.fieldBuilder.isInline(inline);
            return this;
        }

        /**
         * Sets the emoji of the {@link ToggleMenuItem}.
         *
         * @param emoji The emoji of the menu item.
         */
        public ToggleMenuItemBuilder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the emoji of the {@link ToggleMenuItem}.
         *
         * @param emoji The emoji of the menu item.
         */
        public ToggleMenuItemBuilder withEmoji(@NotNull Optional<Emoji> emoji) {
            this.fieldBuilder.withEmoji(emoji);
            return this;
        }

        /**
         * Formats the name of the {@link ToggleMenuItem} with the given objects.
         *
         * @param name The name of the menu item.
         * @param objects Objects used to format the name.
         */
        public ToggleMenuItemBuilder withName(@NotNull String name, @NotNull Object... objects) {
            return this.withName(FormatUtil.format(name, objects));
        }

        /**
         * Sets the name of the {@link ToggleMenuItem}.
         *
         * @param name The name of the menu item.
         */
        public ToggleMenuItemBuilder withName(@Nullable String name) {
            return this.withName(Optional.ofNullable(name));
        }

        /**
         * Optionally sets the name of the {@link ToggleMenuItem}.
         *
         * @param name The name of the menu item.
         */
        public ToggleMenuItemBuilder withName(@NotNull Optional<String> name) {
            this.fieldBuilder.withName(name);
            return this;
        }

        /**
         * Sets the value of the {@link ToggleMenuItem}.
         *
         * @param value The value of the menu item.
         */
        public ToggleMenuItemBuilder withValue(boolean value) {
            this.value = value;
            return this;
        }

        @Override
        public ToggleMenuItem build() {
            return new ToggleMenuItem(
                this.uniqueId,
                this.fieldBuilder
                    .withValue(this.value ? "Yes" : "No")
                    .build(),
                this.value
            );
        }

    }

}