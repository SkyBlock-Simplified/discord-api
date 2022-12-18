package dev.sbs.discordapi.response.menu.item.singleton.string;

import dev.sbs.api.util.builder.Builder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public final class ThumbnailUrlMenuItem extends StringMenuItem {

    private ThumbnailUrlMenuItem(@NotNull UUID uniqueId, @NotNull Optional<String> value) {
        super(uniqueId, Type.THUMBNAIL_URL, value);
    }

    public static ThumbnailUrlMenuItemBuilder builder() {
        return new ThumbnailUrlMenuItemBuilder(UUID.randomUUID());
    }

    public ThumbnailUrlMenuItemBuilder mutate() {
        return new ThumbnailUrlMenuItemBuilder(this.getUniqueId())
            .withValue(this.getValue());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ThumbnailUrlMenuItemBuilder implements Builder<ThumbnailUrlMenuItem> {

        private final UUID uniqueId;
        private Optional<String> value = Optional.empty();

        /**
         * Sets the selected value of the {@link ThumbnailUrlMenuItem}.
         *
         * @param value The selected value of the menu item.
         */
        public ThumbnailUrlMenuItemBuilder withValue(@Nullable String value) {
            return this.withValue(Optional.ofNullable(value));
        }

        /**
         * Sets the selected value of the {@link ThumbnailUrlMenuItem}.
         *
         * @param value The selected value of the menu item.
         */
        public ThumbnailUrlMenuItemBuilder withValue(@NotNull Optional<String> value) {
            this.value = value;
            return this;
        }

        @Override
        public ThumbnailUrlMenuItem build() {
            return new ThumbnailUrlMenuItem(
                this.uniqueId,
                this.value
            );
        }

    }

}