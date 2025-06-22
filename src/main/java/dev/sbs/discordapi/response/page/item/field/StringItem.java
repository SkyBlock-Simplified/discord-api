package dev.sbs.discordapi.response.page.item.field;

import dev.sbs.api.builder.ClassBuilder;
import dev.sbs.api.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.StringUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.embed.structure.Field;
import dev.sbs.discordapi.response.page.item.Item;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class StringItem implements FieldItem<String> {

    private final @NotNull SelectMenu.Option option;
    private final boolean editable;
    private final boolean inline;
    private final @NotNull Optional<String> value;

    @Override
    public @NotNull StringItem applyVariables(@NotNull ConcurrentMap<String, Object> variables) {
        return this.mutate()
            .withValue(this.getValue().map(value -> StringUtil.format(value, variables)))
            .build();
    }

    public static @NotNull Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    public static @NotNull Builder from(@NotNull StringItem item) {
        return builder()
            .withOption(item.getOption())
            .isEditable(item.isEditable())
            .withValue(item.getValue());
    }

    @Override
    public @NotNull String getRenderValue() {
        return this.getValue().orElse("***null***");
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder implements ClassBuilder<StringItem> {

        private final SelectMenu.Option.Builder optionBuilder = SelectMenu.Option.builder();
        private boolean editable;
        private boolean inline;
        private Optional<String> value = Optional.empty();

        /**
         * Sets the {@link Item} as editable.
         */
        public Builder isEditable() {
            return this.isEditable(true);
        }

        /**
         * Set the editable state of the {@link Item}.
         *
         * @param editable The value of the author item.
         */
        public Builder isEditable(boolean editable) {
            this.editable = editable;
            return this;
        }

        /**
         * Sets the {@link Item} as inline.
         */
        public Builder isInline() {
            return this.isInline(true);
        }

        /**
         * Set the inline state of the {@link Item}.
         *
         * @param editable The inline state of the item.
         */
        public Builder isInline(boolean editable) {
            this.inline = editable;
            return this;
        }

        /**
         * Sets the description of the {@link SelectMenu.Option}.
         *
         * @param description The description to use.
         * @see SelectMenu.Option#getDescription()
         */
        public Builder withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        /**
         * Sets the description of the {@link SelectMenu.Option}.
         *
         * @param description The description to use.
         * @param args The objects used to format the description.
         * @see SelectMenu.Option#getDescription()
         */
        public Builder withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        /**
         * Sets the description of the {@link SelectMenu.Option}.
         *
         * @param description The description to use.
         * @see SelectMenu.Option#getDescription()
         */
        public Builder withDescription(@NotNull Optional<String> description) {
            this.optionBuilder.withDescription(description);
            return this;
        }

        /**
         * Sets the emoji of the {@link SelectMenu.Option}.
         *
         * @param emoji The emoji to use.
         * @see SelectMenu.Option#getEmoji()
         * @see Field#getName()
         */
        public Builder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the emoji of the {@link SelectMenu.Option}.
         *
         * @param emoji The emoji to use.
         * @see SelectMenu.Option#getEmoji()
         * @see Field#getName()
         */
        public Builder withEmoji(@NotNull Optional<Emoji> emoji) {
            this.optionBuilder.withEmoji(emoji);
            return this;
        }

        /**
         * Overrides the default identifier of the {@link SelectMenu.Option}.
         *
         * @param identifier The identifier to use.
         * @see SelectMenu.Option#getValue()
         */
        public Builder withIdentifier(@NotNull String identifier) {
            this.optionBuilder.withValue(identifier);
            return this;
        }

        /**
         * Overrides the default identifier of the {@link SelectMenu.Option}.
         *
         * @param identifier The identifier to use.
         * @param args The objects used to format the value.
         * @see SelectMenu.Option#getValue()
         */
        public Builder withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
            this.optionBuilder.withValue(identifier, args);
            return this;
        }

        /**
         * Sets the label of the {@link SelectMenu.Option}.
         *
         * @param label The label of the field item.
         * @see SelectMenu.Option#getLabel()
         */
        public Builder withLabel(@NotNull String label) {
            this.optionBuilder.withLabel(label);
            return this;
        }

        /**
         * Sets the label of the {@link SelectMenu.Option}.
         *
         * @param label The label of the field item.
         * @param args The objects used to format the label.
         * @see SelectMenu.Option#getLabel()
         */
        public Builder withLabel(@PrintFormat @NotNull String label, @Nullable Object... args) {
            this.optionBuilder.withLabel(label, args);
            return this;
        }

        public Builder withOption(@NotNull SelectMenu.Option option) {
            return this.withIdentifier(option.getValue())
                .withDescription(option.getDescription())
                .withEmoji(option.getEmoji())
                .withLabel(option.getLabel());
        }

        /**
         * Sets the selected value of the {@link StringItem}.
         *
         * @param value The value of the item.
         */
        public Builder withValue(@Nullable String value) {
            return this.withValue(Optional.ofNullable(value));
        }

        /**
         * Sets the selected value of the {@link StringItem}.
         *
         * @param value The value of the item.
         * @param args The objects used to format the value.
         */
        public Builder withValue(@PrintFormat @Nullable String value, @Nullable Object... args) {
            return this.withValue(StringUtil.formatNullable(value, args));
        }

        /**
         * Sets the selected value of the {@link StringItem}.
         *
         * @param value The value of the item.
         */
        public Builder withValue(@NotNull Optional<String> value) {
            this.value = value;
            return this;
        }

        @Override
        public @NotNull StringItem build() {
            return new StringItem(
                this.optionBuilder.build(),
                this.editable,
                this.inline,
                this.value
            );
        }

    }

}
