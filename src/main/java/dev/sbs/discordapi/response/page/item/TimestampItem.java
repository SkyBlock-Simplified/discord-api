package dev.sbs.discordapi.response.page.item;

import dev.sbs.api.util.date.RealDate;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.embed.Field;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class TimestampItem extends SingletonItem<Instant> implements SingletonFieldItem {

    private TimestampItem(@NotNull SelectMenu.Option option, boolean editable, @NotNull Optional<Instant> value) {
        super(option, Type.FIELD, editable, value);
    }

    public static Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public Field getRenderField() {
        return Field.builder()
            .withName(this.getOption().map(SelectMenu.Option::getLabel))
            .withValue(this.getValue().map(RealDate::new).map(RealDate::toString))
            .isInline()
            .build();
    }

    public Builder mutate() {
        return new Builder()
            .withValue(this.getValue())
            .isEditable(this.isEditable())
            .withOption(this.getOption().orElseThrow());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder extends PageItemBuilder<TimestampItem> {

        private Optional<Instant> value = Optional.empty();

        public Builder isEditable() {
            return this.isEditable(true);
        }

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

        /**
         * Sets the selected value of the {@link TimestampItem}.
         *
         * @param value The selected value of the menu item.
         */
        public Builder withValue(@Nullable Instant value) {
            return this.withValue(Optional.ofNullable(value));
        }

        /**
         * Sets the selected value of the {@link TimestampItem}.
         *
         * @param value The selected value of the menu item.
         */
        public Builder withValue(@NotNull Optional<Instant> value) {
            this.value = value;
            return this;
        }

        @Override
        public TimestampItem build() {
            return new TimestampItem(
                super.optionBuilder.build(),
                super.editable,
                this.value
            );
        }

    }

}