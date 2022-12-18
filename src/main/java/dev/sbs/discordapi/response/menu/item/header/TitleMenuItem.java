package dev.sbs.discordapi.response.menu.item.header;

import dev.sbs.api.util.builder.Builder;
import dev.sbs.discordapi.response.menu.item.singleton.string.StringMenuItem;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public final class TitleMenuItem extends StringMenuItem {

    private TitleMenuItem(@NotNull UUID uniqueId, @NotNull Optional<String> value) {
        super(uniqueId, Type.TITLE, value);
    }

    public static TitleMenuItemBuilder builder() {
        return new TitleMenuItemBuilder(UUID.randomUUID());
    }

    public TitleMenuItemBuilder mutate() {
        return new TitleMenuItemBuilder(this.getUniqueId())
            .withValue(this.getValue());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class TitleMenuItemBuilder implements Builder<TitleMenuItem> {

        private final UUID uniqueId;
        private Optional<String> value = Optional.empty();

        /**
         * Sets the selected value of the {@link TitleMenuItem}.
         *
         * @param value The selected value of the menu item.
         */
        public TitleMenuItemBuilder withValue(@Nullable String value) {
            return this.withValue(Optional.ofNullable(value));
        }

        /**
         * Sets the selected value of the {@link TitleMenuItem}.
         *
         * @param value The selected value of the menu item.
         */
        public TitleMenuItemBuilder withValue(@NotNull Optional<String> value) {
            this.value = value;
            return this;
        }

        @Override
        public TitleMenuItem build() {
            return new TitleMenuItem(
                this.uniqueId,
                this.value
            );
        }

    }

}