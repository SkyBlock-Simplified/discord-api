package dev.sbs.discordapi.response.page.item;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.collection.concurrent.atomic.AtomicCollection;
import dev.sbs.api.util.data.tuple.Pair;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.StreamUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.util.exception.DiscordException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class FieldItem extends PageItem {

    @Getter private final @NotNull ConcurrentMap<Column, ConcurrentList<String>> data;

    private FieldItem(
        @NotNull SelectMenu.Option option,
        boolean editable,
        @NotNull ConcurrentMap<Column, ConcurrentList<String>> data) {
        super(option.getIdentifier(), Optional.of(option), Type.FIELD, editable);
        this.data = Concurrent.newUnmodifiableMap(data);
    }

    public static Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    public static Builder from(@NotNull FieldItem fieldItem) {
        return new Builder()
            .withData(fieldItem.getData())
            .withOption(fieldItem.getOption().orElseThrow())
            .isEditable(fieldItem.isEditable());
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
    public String getFieldValue(@NotNull PageItem.Style itemStyle, @NotNull Column column) {
        return switch (itemStyle) {
            case FIELD, FIELD_INLINE -> StringUtil.join(this.getAllData(), "\n");
            case LIST, LIST_SINGLE, TABLE -> this.getData(column)
                .stream()
                .collect(StreamUtil.toStringBuilder(true))
                .build();
            case TABLE_DESCRIPTION -> column == Column.ONE ?
                this.getOption().flatMap(SelectMenu.Option::getDescription).orElse("") :
                this.getData(column)
                    .stream()
                    .collect(StreamUtil.toStringBuilder(true))
                    .build();
        };
    }

    public Builder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder extends PageItemBuilder<FieldItem> {

        private final ConcurrentMap<Column, ConcurrentList<String>> data = Concurrent.newMap(
            Pair.of(Column.ONE, Concurrent.newList()),
            Pair.of(Column.TWO, Concurrent.newList()),
            Pair.of(Column.THREE, Concurrent.newList())
        );

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


        public Builder isEditable() {
            return this.isEditable(true);
        }

        public Builder isEditable(boolean value) {
            super.editable = value;
            return this;
        }

        /**
         * Adds the value to the {@link FieldItem} formatted with the given objects.
         *
         * @param value The value of the field item.
         * @param objects Objects used to format the value.
         */
        public Builder withData(@NotNull String value, @NotNull Object... objects) {
            return this.withData(Column.ONE, value, objects);
        }

        /**
         * Adds the value to the {@link FieldItem} formatted with the given objects in the specified column.
         *
         * @param column The column of the field item value.
         * @param value The value of the field item.
         * @param objects The objects used to format the value.
         */
        public Builder withData(@NotNull Column column, @NotNull String value, @NotNull Object... objects) {
            if (column == Column.UNKNOWN)
                throw SimplifiedException.of(DiscordException.class)
                    .withMessage("Column cannot be UNKNOWN!")
                    .build();

            this.data.get(column).add(FormatUtil.format(value, objects));
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
        public FieldItem build() {
            return new FieldItem(
                super.optionBuilder.build(),
                super.editable,
                this.data
            );
        }

    }

}
