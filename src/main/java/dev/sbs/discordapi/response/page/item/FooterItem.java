package dev.sbs.discordapi.response.page.item;

import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public final class FooterItem extends Item {

    @Getter private final @NotNull Optional<String> name;
    @Getter private final @NotNull Optional<String> iconUrl;

    private FooterItem(@NotNull SelectMenu.Option option, boolean editable, @NotNull Optional<String> name, @NotNull Optional<String> iconUrl) {
        super(option, Type.FOOTER, editable);
        this.name = name;
        this.iconUrl = iconUrl;
    }

    public static Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    public Builder mutate() {
        return new Builder()
            .withIconUrl(this.getIconUrl())
            .withName(this.getName())
            .withOption(this.getOption())
            .isEditable(this.isEditable());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder extends ItemBuilder<FooterItem> {

        private Optional<String> name = Optional.empty();
        private Optional<String> iconUrl = Optional.empty();

        @Override
        public Builder isEditable() {
            return this.isEditable(true);
        }

        @Override
        public Builder isEditable(boolean value) {
            super.editable = value;
            return this;
        }

        @Override
        public Builder withDescription(@Nullable String description, @NotNull Object... objects) {
            return this.withDescription(StringUtil.formatNullable(description, objects));
        }

        @Override
        public Builder withDescription(@NotNull Optional<String> description) {
            super.optionBuilder.withDescription(description);
            return this;
        }

        @Override
        public Builder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        @Override
        public Builder withEmoji(@NotNull Optional<Emoji> emoji) {
            super.optionBuilder.withEmoji(emoji);
            return this;
        }

        /**
         * Sets the icon url of the {@link FooterItem}.
         *
         * @param iconUrl The selected value of the menu item.
         * @param objects The objects used to format the icon url.
         */
        public Builder withIconUrl(@Nullable String iconUrl, @NotNull Object... objects) {
            return this.withIconUrl(StringUtil.formatNullable(iconUrl, objects));
        }

        /**
         * Sets the icon url of the {@link FooterItem}.
         *
         * @param iconUrl The selected value of the menu item.
         */
        public Builder withIconUrl(@NotNull Optional<String> iconUrl) {
            this.iconUrl = iconUrl;
            return this;
        }

        @Override
        public Builder withIdentifier(@NotNull String identifier, @NotNull Object... objects) {
            super.optionBuilder.withValue(identifier, objects);
            return this;
        }

        @Override
        public Builder withLabel(@NotNull String label, @NotNull Object... objects) {
            super.optionBuilder.withLabel(label, objects);
            return this;
        }

        /**
         * Sets the name of the {@link FooterItem}.
         *
         * @param name The selected value of the menu item.
         * @param objects The objects used to format the name.
         */
        public Builder withName(@Nullable String name, @NotNull Object... objects) {
            return this.withName(StringUtil.formatNullable(name, objects));
        }

        /**
         * Sets the name of the {@link FooterItem}.
         *
         * @param name The selected value of the menu item.
         */
        public Builder withName(@NotNull Optional<String> name) {
            this.name = name;
            return this;
        }

        public Builder withOption(@NotNull SelectMenu.Option option) {
            return this.withIdentifier(option.getValue())
                .withDescription(option.getDescription())
                .withEmoji(option.getEmoji())
                .withLabel(option.getLabel());
        }

        @Override
        public FooterItem build() {
            return new FooterItem(
                super.optionBuilder.build(),
                super.editable,
                this.name,
                this.iconUrl
            );
        }

    }

}