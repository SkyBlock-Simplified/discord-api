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

public final class AuthorItem extends Item {

    @Getter private final @NotNull Optional<String> name;
    @Getter private final @NotNull Optional<String> iconUrl;
    @Getter private final @NotNull Optional<String> url;

    private AuthorItem(
        @NotNull SelectMenu.Option option,
        boolean editable,
        @NotNull Optional<String> name,
        @NotNull Optional<String> iconUrl,
        @NotNull Optional<String> url) {
        super(option.getIdentifier(), Optional.of(option), Type.AUTHOR, editable);
        this.name = name;
        this.iconUrl = iconUrl;
        this.url = url;
    }

    public static Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    public Builder mutate() {
        return new Builder()
            .withName(this.getName())
            .withIconUrl(this.getIconUrl())
            .withUrl(this.getUrl())
            .withOption(this.getOption().orElseThrow());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder extends ItemBuilder<AuthorItem> {

        private Optional<String> name = Optional.empty();
        private Optional<String> iconUrl = Optional.empty();
        private Optional<String> url = Optional.empty();

        @Override
        public Builder isEditable() {
            return this.isEditable(true);
        }

        @Override
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

        @Override
        public Builder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        @Override
        public Builder withEmoji(@NotNull Optional<Emoji> emoji) {
            super.optionBuilder.withEmoji(emoji);
            return this;
        }

        @Override
        public Builder withIdentifier(@NotNull String identifier, @NotNull Object... objects) {
            super.optionBuilder.withIdentifier(identifier, objects);
            return this;
        }

        /**
         * Sets the icon url of the {@link AuthorItem}.
         *
         * @param iconUrl The selected value of the menu item.
         */
        public Builder withIconUrl(@Nullable String iconUrl) {
            return this.withIconUrl(Optional.ofNullable(iconUrl));
        }

        /**
         * Sets the icon url of the {@link AuthorItem}.
         *
         * @param iconUrl The selected value of the menu item.
         */
        public Builder withIconUrl(@NotNull Optional<String> iconUrl) {
            this.iconUrl = iconUrl;
            return this;
        }

        @Override
        public Builder withLabel(@NotNull String label, @NotNull Object... objects) {
            super.optionBuilder.withLabel(label, objects);
            return this;
        }

        /**
         * Sets the name of the {@link AuthorItem}.
         *
         * @param name The selected value of the menu item.
         */
        public Builder withName(@Nullable String name) {
            return this.withName(Optional.ofNullable(name));
        }

        /**
         * Sets the name of the {@link AuthorItem}.
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

        @Override
        public Builder withOptionValue(@NotNull String value, @NotNull Object... objects) {
            super.optionBuilder.withValue(value, objects);
            return this;
        }

        /**
         * Sets the url of the {@link AuthorItem}.
         *
         * @param url The selected value of the menu item.
         */
        public Builder withUrl(@Nullable String url) {
            return this.withUrl(Optional.ofNullable(url));
        }

        /**
         * Sets the url of the {@link AuthorItem}.
         *
         * @param url The selected value of the menu item.
         */
        public Builder withUrl(@NotNull Optional<String> url) {
            this.url = url;
            return this;
        }

        @Override
        public AuthorItem build() {
            return new AuthorItem(
                super.optionBuilder.build(),
                super.editable,
                this.name,
                this.iconUrl,
                this.url
            );
        }

    }

}