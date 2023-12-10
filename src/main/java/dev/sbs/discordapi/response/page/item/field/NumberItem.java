package dev.sbs.discordapi.response.page.item.field;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.embed.structure.Field;
import dev.sbs.discordapi.response.page.item.type.Item;
import dev.sbs.discordapi.response.page.item.type.RenderItem;
import dev.sbs.discordapi.response.page.item.type.SingletonItem;
import dev.sbs.discordapi.util.DiscordReference;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public final class NumberItem<T extends Number> implements SingletonItem<T>, RenderItem {

    private final @NotNull SelectMenu.Option option;
    private final boolean editable;
    private final @NotNull Optional<T> value;
    private final @NotNull Class<T> numberClass;
    private final @NotNull ConcurrentList<T> options;

    public static <T extends Number> @NotNull Builder<T> builder(@NotNull Class<T> numberClass) {
        return new Builder<>(numberClass).withIdentifier(UUID.randomUUID().toString());
    }

    public static <T extends Number> @NotNull Builder<T> from(@NotNull NumberItem<T> item) {
        return builder(item.getNumberClass())
            .withOption(item.getOption())
            .isEditable(item.isEditable())
            .withValue(item.getValue())
            .withOptions(item.getOptions());
    }

    @Override
    public @NotNull Field getRenderField() {
        return Field.builder()
            .withName(this.getOption().getLabel())
            .withValue(
                this.getValue()
                    .map(T::toString)
                    .orElse(DiscordReference.getEmoji("TEXT_NULL").map(Emoji::asFormat).orElse("***null***"))
            )
            .isInline()
            .build();
    }

    @Override
    public @NotNull Type getType() {
        return Type.FIELD;
    }

    public @NotNull Builder<T> mutate() {
        return from(this);
    }

    @Override
    public boolean isSingular() {
        return false;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<T extends Number> implements dev.sbs.api.util.builder.Builder<NumberItem<T>> {

        private final SelectMenu.Option.Builder optionBuilder = SelectMenu.Option.builder();
        private boolean editable;
        private final Class<T> modelClass;
        private final ConcurrentList<T> options = Concurrent.newList();
        private Optional<T> value = Optional.empty();

        /**
         * Sets the {@link Item} as editable.
         */
        public Builder<T> isEditable() {
            return this.isEditable(true);
        }

        /**
         * Set the editable state of the {@link Item}.
         *
         * @param editable The value of the author item.
         */
        public Builder<T> isEditable(boolean editable) {
            this.editable = editable;
            return this;
        }

        /**
         * Sets the description of the {@link SelectMenu.Option}.
         *
         * @param description The description to use.
         * @see SelectMenu.Option#getDescription()
         */
        public Builder<T> withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        /**
         * Sets the description of the {@link SelectMenu.Option}.
         *
         * @param description The description to use.
         * @param args The objects used to format the description.
         * @see SelectMenu.Option#getDescription()
         */
        public Builder<T> withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        /**
         * Sets the description of the {@link SelectMenu.Option}.
         *
         * @param description The description to use.
         * @see SelectMenu.Option#getDescription()
         */
        public Builder<T> withDescription(@NotNull Optional<String> description) {
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
        public Builder<T> withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the emoji of the {@link SelectMenu.Option}.
         *
         * @param emoji The emoji to use.
         * @see SelectMenu.Option#getEmoji()
         * @see Field#getName()
         */
        public Builder<T> withEmoji(@NotNull Optional<Emoji> emoji) {
            this.optionBuilder.withEmoji(emoji);
            return this;
        }

        /**
         * Overrides the default identifier of the {@link SelectMenu.Option}.
         *
         * @param identifier The identifier to use.
         * @see SelectMenu.Option#getValue()
         */
        public Builder<T> withIdentifier(@NotNull String identifier) {
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
        public Builder<T> withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
            this.optionBuilder.withValue(identifier, args);
            return this;
        }

        /**
         * Sets the label of the {@link SelectMenu.Option}.
         *
         * @param label The label of the field item.
         * @see SelectMenu.Option#getLabel()
         */
        public Builder<T> withLabel(@NotNull String label) {
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
        public Builder<T> withLabel(@PrintFormat @NotNull String label, @Nullable Object... args) {
            this.optionBuilder.withLabel(label, args);
            return this;
        }

        public Builder<T> withOption(@NotNull SelectMenu.Option option) {
            return this.withIdentifier(option.getValue())
                .withDescription(option.getDescription())
                .withEmoji(option.getEmoji())
                .withLabel(option.getLabel());
        }

        /**
         * The options available for selection {@link NumberItem}.
         *
         * @param options The options available for selection.
         */
        public Builder<T> withOptions(@NotNull T... options) {
            return this.withOptions(Arrays.asList(options));
        }

        /**
         * The options available for selection {@link NumberItem}.
         *
         * @param options The options available for selection.
         */
        public Builder<T> withOptions(@NotNull Iterable<T> options) {
            this.options.clear();
            options.forEach(this.options::add);
            return this;
        }

        /**
         * Sets the selected value of the {@link NumberItem}.
         *
         * @param selected The selected value of the menu item.
         */
        public Builder<T> withValue(@Nullable T selected) {
            return this.withValue(Optional.ofNullable(selected));
        }

        /**
         * Sets the selected value of the {@link NumberItem}.
         *
         * @param selected The selected value of the menu item.
         */
        public Builder<T> withValue(@NotNull Optional<T> selected) {
            this.value = selected;
            return this;
        }

        @Override
        public @NotNull NumberItem<T> build() {
            return new NumberItem<>(
                this.optionBuilder.build(),
                this.editable,
                this.value,
                this.modelClass,
                this.options
            );
        }

    }

}