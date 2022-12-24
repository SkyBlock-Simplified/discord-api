package dev.sbs.discordapi.response.page.item;

import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public final class FooterItem extends PageItem {

    @Getter private final @NotNull Optional<String> name;
    @Getter private final @NotNull Optional<String> iconUrl;

    private FooterItem(@NotNull SelectMenu.Option option, boolean editable, @NotNull Optional<String> name, @NotNull Optional<String> iconUrl) {
        super(option.getIdentifier(), Optional.of(option), Type.FOOTER, editable);
        this.name = name;
        this.iconUrl = iconUrl;
    }

    public static Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public String getFieldValue(@NotNull Style itemStyle, @NotNull Column column) {
        return null; // TODO: NOT IMPLEMENTED
    }

    public Builder mutate() {
        return new Builder()
            .withIconUrl(this.getIconUrl())
            .withName(this.getName())
            .withOption(this.getOption().orElseThrow())
            .isEditable(this.isEditable());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder extends PageItemBuilder<FooterItem> {

        private Optional<String> name = Optional.empty();
        private Optional<String> iconUrl = Optional.empty();

        public Builder isEditable() {
            return this.isEditable(true);
        }

        public Builder isEditable(boolean value) {
            super.editable = value;
            return this;
        }

        public Builder withDescription(@Nullable String description, @NotNull Object... objects) {
            return this.withDescription(FormatUtil.formatNullable(description, objects));
        }

        public Builder withDescription(@NotNull Optional<String> description) {
            super.optionBuilder.withDescription(description);
            return this;
        }

        public Builder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

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
            return this.withIconUrl(FormatUtil.formatNullable(iconUrl, objects));
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

        public Builder withIdentifier(@NotNull String identifier, @NotNull Object... objects) {
            super.optionBuilder.withIdentifier(identifier, objects);
            return this;
        }

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
            return this.withName(FormatUtil.formatNullable(name, objects));
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
            return this.withIdentifier(option.getIdentifier())
                .withDescription(option.getDescription())
                .withEmoji(option.getEmoji())
                .withLabel(option.getLabel())
                .withOptionValue(option.getValue());
        }

        public Builder withOptionValue(@NotNull String value, @NotNull Object... objects) {
            super.optionBuilder.withValue(value, objects);
            return this;
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