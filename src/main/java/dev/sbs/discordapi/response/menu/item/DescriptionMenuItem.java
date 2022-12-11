package dev.sbs.discordapi.response.menu.item;

import dev.sbs.api.util.builder.Builder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public final class DescriptionMenuItem extends MenuItem {

    @Getter private final @NotNull Optional<String> value;

    private DescriptionMenuItem(
        @NotNull UUID uniqueId,
        @NotNull Optional<String> value
    ) {
        super(uniqueId);
        this.value = value;
    }

    public static DescriptionMenuItemBuilder builder() {
        return new DescriptionMenuItemBuilder(UUID.randomUUID());
    }

    public DescriptionMenuItemBuilder mutate() {
        return new DescriptionMenuItemBuilder(this.getUniqueId())
            .withValue(this.getValue());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class DescriptionMenuItemBuilder implements Builder<DescriptionMenuItem> {

        private final UUID uniqueId;
        private Optional<String> value = Optional.empty();

        /**
         * Sets the selected value of the {@link DescriptionMenuItem}.
         *
         * @param value The selected value of the menu item.
         */
        public DescriptionMenuItemBuilder withValue(@Nullable String value) {
            return this.withValue(Optional.ofNullable(value));
        }

        /**
         * Sets the selected value of the {@link DescriptionMenuItem}.
         *
         * @param value The selected value of the menu item.
         */
        public DescriptionMenuItemBuilder withValue(@NotNull Optional<String> value) {
            this.value = value;
            return this;
        }

        @Override
        public DescriptionMenuItem build() {
            return new DescriptionMenuItem(
                this.uniqueId,
                this.value
            );
        }

    }

}