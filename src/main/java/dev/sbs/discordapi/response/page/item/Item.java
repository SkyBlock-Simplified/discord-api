package dev.sbs.discordapi.response.page.item;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.data.model.discord.emojis.EmojiModel;
import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.data.tuple.Triple;
import dev.sbs.api.util.helper.StreamUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.response.page.Page;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Item {

    @Getter private static final Emoji nullEmoji = SimplifiedApi.getRepositoryOf(EmojiModel.class)
        .findFirst(EmojiModel::getKey, "TEXT_NULL")
        .flatMap(Emoji::of)
        .orElseThrow();
    @Getter private final @NotNull SelectMenu.Option option;
    @Getter private final @NotNull Type type;
    @Getter private final boolean editable;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Item item = (Item) o;

        return new EqualsBuilder()
            .append(this.getIdentifier(), item.getIdentifier())
            .append(this.getOption(), item.getOption())
            .append(this.getType(), item.getType())
            .append(this.isEditable(), item.isEditable())
            .build();
    }

    public final @NotNull String getIdentifier() {
        return this.getOption().getValue();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getIdentifier())
            .append(this.getOption())
            .append(this.getType())
            .append(this.isEditable())
            .build();
    }

    @RequiredArgsConstructor
    public enum Column {

        UNKNOWN(-1),
        ONE(1),
        TWO(2),
        THREE(3);

        @Getter private final int value;

        public static Column of(int value) {
            return Arrays.stream(values())
                .filter(column -> Objects.equals(column.getValue(), value))
                .findFirst()
                .orElse(UNKNOWN);
        }

    }

    protected static abstract class ItemBuilder<T> implements Builder<T> {

        protected final SelectMenu.Option.Builder optionBuilder = SelectMenu.Option.builder();
        protected boolean editable;

        /**
         * Sets the {@link Item} as editable.
         */
        public abstract ItemBuilder<T> isEditable();

        /**
         * Set the editable state of the {@link Item}.
         *
         * @param value The value of the menu item.
         */
        public abstract ItemBuilder<T> isEditable(boolean value);

        /**
         * Sets the description of the {@link Item}.
         * <br><br>
         * This is used for the {@link SelectMenu.Option#getDescription()}.
         *
         * @param description The description to use.
         * @param objects The objects used to format the description.
         */
        public abstract ItemBuilder<T> withDescription(@Nullable String description, @NotNull Object... objects);

        /**
         * Sets the description of the {@link Item}.
         * <br><br>
         * This is used for the {@link SelectMenu.Option#getDescription()}.
         *
         * @param description The description to use.
         */
        public abstract ItemBuilder<T> withDescription(@NotNull Optional<String> description);

        /**
         * Sets the emoji of the {@link Item}.
         * <br><br>
         * This is used for the {@link SelectMenu.Option#getEmoji()} and {@link Field#getName()}.
         *
         * @param emoji The emoji to use.
         */
        public abstract ItemBuilder<T> withEmoji(@Nullable Emoji emoji);

        /**
         * Sets the emoji of the {@link Item}.
         * <br><br>
         * This is used for the {@link SelectMenu.Option#getEmoji()} and {@link Field#getName()}.
         *
         * @param emoji The emoji to use.
         */
        public abstract ItemBuilder<T> withEmoji(@NotNull Optional<Emoji> emoji);

        /**
         * Overrides the default identifier of the {@link Item}.
         * <br><br>
         * This is used for the {@link SelectMenu.Option#getValue()} and {@link Item}.
         *
         * @param identifier The identifier to use.
         * @param objects The objects used to format the value.
         */
        public abstract ItemBuilder<T> withIdentifier(@NotNull String identifier, @NotNull Object... objects);

        /**
         * Sets the label of the {@link Item}.
         * <br><br>
         * This is used for the {@link SelectMenu.Option}.
         *
         * @param label The label of the field item.
         * @param objects The objects used to format the label.
         */
        public abstract ItemBuilder<T> withLabel(@NotNull String label, @NotNull Object... objects);

    }

    @RequiredArgsConstructor
    public enum Style {

        /**
         * Displays {@link Item} into a single {@link Field}.
         * <br><br>
         * - Only uses column 1 data.
         * <br>
         * - Field names and emojis are handled by the {@link Item}.
         */
        FIELD(false),
        /**
         * Displays {@link Item} into a single inline {@link Field}.
         * <br><br>
         * - Only uses column 1 data.
         * <br>
         * Field names and emojis are handled by the {@link Item}.
         */
        FIELD_INLINE(true),
        /**
         * Displays all {@link Item Items} in 3 columns as a list of data.
         * <br><br>
         * Field names and emojis are handled by column data specified on the {@link Page}.
         */
        LIST(true),
        /**
         * Displays all {@link Item Items} in 1 column as a list of data.
         * <br><br>
         * Field names and emojis are handled by column data specified on the {@link Page}.
         */
        LIST_SINGLE(false),
        /**
         * Displays {@link Item Items} 3 columns across 3 {@link Field Fields}.
         * <br><br>
         * - The left field name and emoji are handled by the {@link Item}.
         * <br>
         * - The left field value is provided by column 1 data specified by the {@link Item}.
         * <br>
         * - The middle and right field names are handled by the column data specified on the {@link Page}.
         */
        TABLE(true),
        /**
         * Displays {@link Item Items} 3 columns across 3 {@link Field Fields}.
         * <br><br>
         * - The left field name and emoji are handled by the {@link Item}.
         * <br>
         * - The left field value is provided by description specified by the {@link Item}.
         * <br>
         * - The middle and right field names are handled by the column data specified on the {@link Page}.
         */
        TABLE_DESCRIPTION(true);

        private static final Triple<String, String, String> NOOP_HANDLER = Triple.of(null, null, null);
        @Getter private final boolean inline;

        public ConcurrentList<Field> getPageItems(@NotNull Optional<Triple<String, String, String>> columnNames, @NotNull ConcurrentList<Item> items) {
            return this.getPageItems(columnNames.orElse(NOOP_HANDLER), items);
        }

        private ConcurrentList<Field> getPageItems(@NotNull Triple<String, String, String> columnNames, @NotNull ConcurrentList<Item> items) {
            return switch (this) {
                case FIELD, FIELD_INLINE -> items.stream()
                    .filter(pageItem -> pageItem.getType() == Type.FIELD)
                    .filter(pageItem -> pageItem.getClass().isAssignableFrom(FieldItem.class))
                    .map(FieldItem.class::cast)
                    .map(fieldItem -> Field.builder()
                        .withEmoji(fieldItem.getOption().getEmoji())
                        .withName(fieldItem.getOption().getLabel())
                        .withValue(fieldItem.getFieldValue(this, Item.Column.ONE))
                        .isInline(this.isInline())
                        .build()
                    )
                    .collect(Concurrent.toList());
                case LIST -> Concurrent.newList(
                    Field.builder()
                        .withName(columnNames.getLeft())
                        .withValue(
                            items.stream()
                                .filter(pageItem -> pageItem.getType() == Type.FIELD)
                                .filter(pageItem -> pageItem.getClass().isAssignableFrom(FieldItem.class))
                                .map(FieldItem.class::cast)
                                .map(pageItem -> pageItem.getFieldValue(this, Item.Column.ONE))
                                .collect(StreamUtil.toStringBuilder(true))
                                .build()
                        )
                        .isInline(this.isInline())
                        .build(),
                    Field.builder()
                        .withName(columnNames.getMiddle())
                        .withValue(
                            items.stream()
                                .filter(pageItem -> pageItem.getType() == Type.FIELD)
                                .filter(pageItem -> pageItem.getClass().isAssignableFrom(FieldItem.class))
                                .map(FieldItem.class::cast)
                                .map(pageItem -> pageItem.getFieldValue(this, Item.Column.TWO))
                                .collect(StreamUtil.toStringBuilder(true))
                                .build()
                        )
                        .isInline(this.isInline())
                        .build(),
                    Field.builder()
                        .withName(columnNames.getRight())
                        .withValue(
                            items.stream()
                                .filter(pageItem -> pageItem.getType() == Type.FIELD)
                                .filter(pageItem -> pageItem.getClass().isAssignableFrom(FieldItem.class))
                                .map(FieldItem.class::cast)
                                .map(pageItem -> pageItem.getFieldValue(this, Item.Column.THREE))
                                .collect(StreamUtil.toStringBuilder(true))
                                .build()
                        )
                        .isInline(this.isInline())
                        .build()
                );
                case LIST_SINGLE -> Concurrent.newList(
                    Field.builder()
                        .withName(columnNames.getLeft())
                        .withValue(
                            items.stream()
                                .filter(pageItem -> pageItem.getType() == Type.FIELD)
                                .filter(pageItem -> pageItem.getClass().isAssignableFrom(FieldItem.class))
                                .map(FieldItem.class::cast)
                                .map(pageItem -> pageItem.getFieldValue(this, Item.Column.ONE))
                                .collect(StreamUtil.toStringBuilder(true))
                                .build()
                        )
                        .isInline(this.isInline())
                        .build()
                );
                case TABLE, TABLE_DESCRIPTION -> items.stream()
                    .filter(pageItem -> pageItem.getType() == Type.FIELD)
                    .filter(pageItem -> pageItem.getClass().isAssignableFrom(FieldItem.class))
                    .map(FieldItem.class::cast)
                    .map(pageItem -> Concurrent.newList(
                        Field.builder()
                            .withEmoji(pageItem.getOption().getEmoji())
                            .withName(pageItem.getOption().getLabel())
                            .withValue(pageItem.getFieldValue(this, Item.Column.ONE))
                            .isInline()
                            .build(),
                        Field.builder()
                            .withName(columnNames.getMiddle())
                            .withValue(pageItem.getFieldValue(this, Item.Column.TWO))
                            .isInline()
                            .build(),
                        Field.builder()
                            .withName(columnNames.getRight())
                            .withValue(pageItem.getFieldValue(this, Item.Column.THREE))
                            .isInline()
                            .build()
                    ))
                    .flatMap(ConcurrentList::stream)
                    .collect(Concurrent.toList());
            };
        }

    }

    @RequiredArgsConstructor
    public enum Type {

        UNKNOWN(-1, true),
        PAGE(1, true),
        AUTHOR(2, false),
        TITLE(3, false),
        DESCRIPTION(4, false),
        THUMBNAIL_URL(5, false),
        IMAGE_URL(6, false),
        FIELD(7, true),
        FOOTER(8, false);

        @Getter private final int value;

        @Getter private final boolean fieldRender;

        public static Type of(int value) {
            return Arrays.stream(values())
                .filter(style -> style.getValue() == value)
                .findFirst()
                .orElse(UNKNOWN);
        }

    }

}
