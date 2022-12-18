package dev.sbs.discordapi.response.menu.item.footer;

import dev.sbs.api.util.builder.Builder;
import dev.sbs.discordapi.response.menu.item.MenuItem;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public final class FooterMenuItem extends MenuItem {

    @Getter private final @NotNull Optional<String> name;
    @Getter private final @NotNull Optional<String> iconUrl;

    private FooterMenuItem(@NotNull UUID uniqueId, @NotNull Optional<String> name, @NotNull Optional<String> iconUrl) {
        super(uniqueId, Type.FOOTER);
        this.name = name;
        this.iconUrl = iconUrl;
    }

    public static FooterMenuItemBuilder builder() {
        return new FooterMenuItemBuilder(UUID.randomUUID());
    }

    public FooterMenuItemBuilder mutate() {
        return new FooterMenuItemBuilder(this.getUniqueId())
            .withName(this.getName());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class FooterMenuItemBuilder implements Builder<FooterMenuItem> {

        private final UUID uniqueId;
        private Optional<String> name = Optional.empty();
        private Optional<String> iconUrl = Optional.empty();

        /**
         * Sets the icon url of the {@link FooterMenuItem}.
         *
         * @param iconUrl The selected value of the menu item.
         */
        public FooterMenuItemBuilder withIconUrl(@Nullable String iconUrl) {
            return this.withIconUrl(Optional.ofNullable(iconUrl));
        }

        /**
         * Sets the icon url of the {@link FooterMenuItem}.
         *
         * @param iconUrl The selected value of the menu item.
         */
        public FooterMenuItemBuilder withIconUrl(@NotNull Optional<String> iconUrl) {
            this.iconUrl = iconUrl;
            return this;
        }

        /**
         * Sets the name of the {@link FooterMenuItem}.
         *
         * @param name The selected value of the menu item.
         */
        public FooterMenuItemBuilder withName(@Nullable String name) {
            return this.withName(Optional.ofNullable(name));
        }

        /**
         * Sets the name of the {@link FooterMenuItem}.
         *
         * @param name The selected value of the menu item.
         */
        public FooterMenuItemBuilder withName(@NotNull Optional<String> name) {
            this.name = name;
            return this;
        }

        @Override
        public FooterMenuItem build() {
            return new FooterMenuItem(
                this.uniqueId,
                this.name,
                this.iconUrl
            );
        }

    }

}