package dev.sbs.discordapi.response.page.item.field;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.collection.concurrent.atomic.AtomicCollection;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.api.util.mutable.tuple.pair.Pair;
import dev.sbs.api.util.stream.StreamUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.embed.structure.Field;
import dev.sbs.discordapi.response.page.item.type.Item;
import dev.sbs.discordapi.response.page.item.type.RenderItem;
import dev.sbs.discordapi.util.exception.DiscordException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Getter
@RequiredArgsConstructor
public class FieldItem implements Item, RenderItem {

    private final @NotNull SelectMenu.Option option;
    private final boolean editable;
    private final ConcurrentMap<Item.Column, ConcurrentList<String>> data;

    @Override
    public @NotNull FieldItem applyVariables(@NotNull ConcurrentMap<String, Object> variables) {
        return this.mutate()
            .withData(
                this.getData()
                    .stream()
                    .map((column, values) -> Pair.of(
                        column,
                        values.stream()
                            .map(value -> StringUtil.format(value, variables))
                            .collect(Concurrent.toList())
                    ))
                    .collect(Concurrent.toMap())
            )
            .build();
    }

    public static @NotNull Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    public static @NotNull Builder from(@NotNull FieldItem item) {
        return builder()
            .withOption(item.getOption())
            .isEditable(item.isEditable())
            .withData(item.getData());
    }

    public ConcurrentList<String> getAllData() {
        return Stream.concat(
                Stream.concat(
                    this.getData(Column.ONE).stream(),
                    this.getData(Column.TWO).stream()
                ),
                this.getData(Column.THREE).stream()
            )
            .collect(Concurrent.toList());
    }

    public ConcurrentList<String> getData(@NotNull Column column) {
        if (column == Column.UNKNOWN)
            throw SimplifiedException.of(DiscordException.class)
                .withMessage("Column cannot be UNKNOWN!")
                .build();

        return this.getData().get(column);
    }

    @Override
    public @NotNull Field getRenderField() {
        return Field.builder()
            .withName(this.getOption().getLabel())
            .withValue(Optional.ofNullable(StringUtil.stripToNull(StringUtil.join(this.getAllData(), "\n"))).orElse("*null*"/*getNullEmoji().asFormat()*/)) // TODO
            .isInline()
            .build();
    }

    public String getFieldValue(@NotNull Item.Style itemStyle, @NotNull Column column) {
        return switch (itemStyle) {
            case FIELD, FIELD_INLINE -> StringUtil.join(this.getAllData(), "\n");
            case LIST, LIST_SINGLE, TABLE -> this.getData(column)
                .stream()
                .collect(StreamUtil.toStringBuilder(true))
                .build();
            case TABLE_DESCRIPTION -> column == Column.ONE ?
                this.getOption().getDescription().orElse("") :
                this.getData(column)
                    .stream()
                    .collect(StreamUtil.toStringBuilder(true))
                    .build();
        };
    }

    @Override
    public @NotNull Type getType() {
        return Type.FIELD;
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @Override
    public boolean isSingular() {
        return false;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder implements dev.sbs.api.util.builder.Builder<FieldItem> {

        private final SelectMenu.Option.Builder optionBuilder = SelectMenu.Option.builder();
        private boolean editable;
        private final ConcurrentMap<Column, ConcurrentList<String>> data = Concurrent.newMap(
            Pair.of(Column.ONE, Concurrent.newList()),
            Pair.of(Column.TWO, Concurrent.newList()),
            Pair.of(Column.THREE, Concurrent.newList())
        );

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
         * Clears all data from all columns.
         */
        public Builder clearData() {
            return this.clearData(Column.values());
        }

        /**
         * Clears all data from all specified columns.
         */
        public Builder clearData(@NotNull Column... columns) {
            Arrays.stream(columns)
                .filter(this.data::containsKey)
                .map(this.data::get)
                .forEach(AtomicCollection::clear);

            return this;
        }

        /**
         * Adds the value to the {@link FieldItem} formatted with the given objects.
         *
         * @param value The value of the field item.
         */
        public Builder withData(@NotNull String value) {
            return this.withData(Column.ONE, value);
        }

        /**
         * Adds the value to the {@link FieldItem} formatted with the given objects.
         *
         * @param value The value of the field item.
         */
        @SuppressWarnings("all")
        public Builder withData(@NotNull Column column, @NotNull String value) {
            return this.withData(column, value, null);
        }

        /**
         * Adds the value to the {@link FieldItem} formatted with the given objects.
         *
         * @param value The value of the field item.
         * @param args Objects used to format the value.
         */
        public Builder withData(@PrintFormat @NotNull String value, @Nullable Object... args) {
            return this.withData(Column.ONE, value, args);
        }

        /**
         * Adds the value to the {@link FieldItem} formatted with the given objects in the specified column.
         *
         * @param column The column of the field item value.
         * @param value The value of the field item.
         * @param args The objects used to format the value.
         */
        public Builder withData(@NotNull Column column, @PrintFormat @NotNull String value, @Nullable Object... args) {
            if (column == Column.UNKNOWN)
                throw SimplifiedException.of(DiscordException.class)
                    .withMessage("Column cannot be UNKNOWN!")
                    .build();

            this.data.get(column).add(String.format(value, args));
            return this;
        }

        /**
         * Adds all the data to the {@link FieldItem}.
         *
         * @param data The data to add.
         */
        public Builder withData(@NotNull ConcurrentMap<Column, ConcurrentList<String>> data) {
            data.stream()
                .filter(entry -> this.data.containsKey(entry.getKey()))
                .forEach(entry -> this.data.get(entry.getKey()).addAll(entry.getValue()));

            return this;
        }

        @Override
        public @NotNull FieldItem build() {
            return new FieldItem(
                this.optionBuilder.build(),
                this.editable,
                this.data
            );
        }

    }

}
