package dev.sbs.discordapi.response.menu.item.header;

import dev.sbs.api.util.builder.Builder;
import dev.sbs.discordapi.response.menu.item.MenuItem;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public final class AuthorMenuItem extends MenuItem {

    @Getter private final @NotNull Optional<String> name;
    @Getter private final @NotNull Optional<String> iconUrl;
    @Getter private final @NotNull Optional<String> url;

    private AuthorMenuItem(@NotNull UUID uniqueId, @NotNull Optional<String> name, @NotNull Optional<String> iconUrl, @NotNull Optional<String> url) {
        super(uniqueId, Type.AUTHOR);
        this.name = name;
        this.iconUrl = iconUrl;
        this.url = url;
    }

    public static AuthorMenuItemBuilder builder() {
        return new AuthorMenuItemBuilder(UUID.randomUUID());
    }

    public AuthorMenuItemBuilder mutate() {
        return new AuthorMenuItemBuilder(this.getUniqueId())
            .withName(this.getName());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class AuthorMenuItemBuilder implements Builder<AuthorMenuItem> {

        private final UUID uniqueId;
        private Optional<String> name = Optional.empty();
        private Optional<String> iconUrl = Optional.empty();
        private Optional<String> url = Optional.empty();

        /**
         * Sets the icon url of the {@link AuthorMenuItem}.
         *
         * @param iconUrl The selected value of the menu item.
         */
        public AuthorMenuItemBuilder withIconUrl(@Nullable String iconUrl) {
            return this.withIconUrl(Optional.ofNullable(iconUrl));
        }

        /**
         * Sets the icon url of the {@link AuthorMenuItem}.
         *
         * @param iconUrl The selected value of the menu item.
         */
        public AuthorMenuItemBuilder withIconUrl(@NotNull Optional<String> iconUrl) {
            this.iconUrl = iconUrl;
            return this;
        }

        /**
         * Sets the name of the {@link AuthorMenuItem}.
         *
         * @param name The selected value of the menu item.
         */
        public AuthorMenuItemBuilder withName(@Nullable String name) {
            return this.withName(Optional.ofNullable(name));
        }

        /**
         * Sets the name of the {@link AuthorMenuItem}.
         *
         * @param name The selected value of the menu item.
         */
        public AuthorMenuItemBuilder withName(@NotNull Optional<String> name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the url of the {@link AuthorMenuItem}.
         *
         * @param url The selected value of the menu item.
         */
        public AuthorMenuItemBuilder withUrl(@Nullable String url) {
            return this.withUrl(Optional.ofNullable(url));
        }

        /**
         * Sets the url of the {@link AuthorMenuItem}.
         *
         * @param url The selected value of the menu item.
         */
        public AuthorMenuItemBuilder withUrl(@NotNull Optional<String> url) {
            this.url = url;
            return this;
        }

        @Override
        public AuthorMenuItem build() {
            return new AuthorMenuItem(
                this.uniqueId,
                this.name,
                this.iconUrl,
                this.url
            );
        }

    }

}