package dev.sbs.discordapi.response.page.item;

import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public final class ImageUrlItem extends SingletonItem<String> {

    private ImageUrlItem(
        @NotNull SelectMenu.Option option,
        boolean editable,
        @NotNull Optional<String> value) {
        super(option, Type.IMAGE_URL, editable, value);
    }

    public static Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    public Builder mutate() {
        return new Builder().withValue(this.getValue())
            .withOption(this.getOption().orElseThrow());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder extends ItemBuilder<ImageUrlItem> {

        private Optional<String> value = Optional.empty();

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
            return this.withDescription(FormatUtil.formatNullable(description, objects));
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

        @Override
        public Builder withIdentifier(@NotNull String identifier, @NotNull Object... objects) {
            super.optionBuilder.withIdentifier(identifier, objects);
            return this;
        }

        @Override
        public Builder withLabel(@NotNull String label, @NotNull Object... objects) {
            super.optionBuilder.withLabel(label, objects);
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
         * Sets the selected value of the {@link ImageUrlItem}.
         *
         * @param value The selected value of the menu item.
         */
        public Builder withValue(@Nullable String value) {
            return this.withValue(Optional.ofNullable(value));
        }

        /**
         * Sets the selected value of the {@link ImageUrlItem}.
         *
         * @param value The selected value of the menu item.
         */
        public Builder withValue(@NotNull Optional<String> value) {
            this.value = value;
            return this;
        }

        @Override
        public ImageUrlItem build() {
            return new ImageUrlItem(
                super.optionBuilder.build(),
                super.editable,
                this.value
            );
        }

    }

}