package dev.sbs.discordapi.response.page.item;

import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.embed.Field;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public final class ToggleItem extends SingletonItem<Boolean> implements SingletonFieldItem {

    private ToggleItem(@NotNull SelectMenu.Option option, boolean editable, Boolean value) {
        super(option, Type.FIELD, editable, Optional.of(value));
    }

    public static Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public Field getRenderField() {
        return Field.builder()
            .withName(this.getOption().map(SelectMenu.Option::getLabel))
            .withValue(this.getValue().map(String::valueOf).orElse("**null**"))
            .isInline()
            .build();
    }

    public Builder mutate() {
        return new Builder()
            .setEnabled(this.getValue().orElse(false))
            .withOption(this.getOption().orElseThrow())
            .isEditable(this.isEditable());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder extends PageItemBuilder<ToggleItem> {

        private Boolean value;

        public Builder isEditable() {
            return this.isEditable(true);
        }

        public Builder isEditable(boolean value) {
            super.editable = value;
            return this;
        }

        /**
         * Disables the value of the {@link ToggleItem}.
         */
        public Builder setDisabled() {
            return this.setDisabled(true);
        }

        /**
         * Sets the status of the {@link ToggleItem}.
         *
         * @param value The value of the menu item.
         */
        public Builder setDisabled(boolean value) {
            this.value = !value;
            return this;
        }

        /**
         * Enables the value of the {@link ToggleItem}.
         */
        public Builder setEnabled() {
            return this.setEnabled(true);
        }

        /**
         * Sets the status of the {@link ToggleItem}.
         *
         * @param value The value of the menu item.
         */
        public Builder setEnabled(boolean value) {
            this.value = value;
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

        public Builder withIdentifier(@NotNull String identifier, @NotNull Object... objects) {
            super.optionBuilder.withIdentifier(identifier, objects);
            return this;
        }

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

        public Builder withOptionValue(@NotNull String value, @NotNull Object... objects) {
            super.optionBuilder.withValue(value, objects);
            return this;
        }

        @Override
        public ToggleItem build() {
            return new ToggleItem(
                super.optionBuilder.build(),
                super.editable,
                this.value
            );
        }

    }

}