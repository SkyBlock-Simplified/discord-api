package dev.sbs.discordapi.response.menu.item.singleton.string;

import dev.sbs.api.util.builder.Builder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public final class ImageUrlMenuItem extends StringMenuItem {

    private ImageUrlMenuItem(@NotNull UUID uniqueId, @NotNull Optional<String> value) {
        super(uniqueId, Type.IMAGE_URL, value);
    }

    public static ImageUrlMenuItemBuilder builder() {
        return new ImageUrlMenuItemBuilder(UUID.randomUUID());
    }

    public ImageUrlMenuItemBuilder mutate() {
        return new ImageUrlMenuItemBuilder(this.getUniqueId())
            .withValue(this.getValue());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ImageUrlMenuItemBuilder implements Builder<ImageUrlMenuItem> {

        private final UUID uniqueId;
        private Optional<String> value = Optional.empty();

        /**
         * Sets the selected value of the {@link ImageUrlMenuItem}.
         *
         * @param value The selected value of the menu item.
         */
        public ImageUrlMenuItemBuilder withValue(@Nullable String value) {
            return this.withValue(Optional.ofNullable(value));
        }

        /**
         * Sets the selected value of the {@link ImageUrlMenuItem}.
         *
         * @param value The selected value of the menu item.
         */
        public ImageUrlMenuItemBuilder withValue(@NotNull Optional<String> value) {
            this.value = value;
            return this;
        }

        @Override
        public ImageUrlMenuItem build() {
            return new ImageUrlMenuItem(
                this.uniqueId,
                this.value
            );
        }

    }

}