package dev.sbs.discordapi.response.menu.item.singleton;

import dev.sbs.api.util.builder.Builder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class TimestampMenuItem extends SingletonMenuItem<Instant> {

    private TimestampMenuItem(@NotNull UUID uniqueId, @NotNull Optional<Instant> value) {
        super(uniqueId, Type.TIMESTAMP, value);
    }

    public static TimestampMenuItemBuilder builder() {
        return new TimestampMenuItemBuilder(UUID.randomUUID());
    }

    public TimestampMenuItemBuilder mutate() {
        return new TimestampMenuItemBuilder(this.getUniqueId())
            .withValue(this.getValue());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class TimestampMenuItemBuilder implements Builder<TimestampMenuItem> {

        private final UUID uniqueId;
        private Optional<Instant> value = Optional.empty();

        /**
         * Sets the selected value of the {@link TimestampMenuItem}.
         *
         * @param value The selected value of the menu item.
         */
        public TimestampMenuItemBuilder withValue(@Nullable Instant value) {
            return this.withValue(Optional.ofNullable(value));
        }

        /**
         * Sets the selected value of the {@link TimestampMenuItem}.
         *
         * @param value The selected value of the menu item.
         */
        public TimestampMenuItemBuilder withValue(@NotNull Optional<Instant> value) {
            this.value = value;
            return this;
        }

        @Override
        public TimestampMenuItem build() {
            return new TimestampMenuItem(
                this.uniqueId,
                this.value
            );
        }

    }

}