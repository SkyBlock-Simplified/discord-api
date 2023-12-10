package dev.sbs.discordapi.response.page.item.type;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.data.tuple.triple.Triple;
import dev.sbs.api.util.stream.StreamUtil;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.embed.structure.Field;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.item.field.FieldItem;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public interface Item {

    default @NotNull String getIdentifier() {
        return this.getOption().getValue();
    }

    @NotNull SelectMenu.Option getOption();

    @NotNull Type getType();

    boolean isEditable();

    @Getter
    @RequiredArgsConstructor
    enum Column {

        UNKNOWN(-1),
        ONE(1),
        TWO(2),
        THREE(3);

        private final int value;

        public static Column of(int value) {
            return Arrays.stream(values())
                .filter(column -> Objects.equals(column.getValue(), value))
                .findFirst()
                .orElse(UNKNOWN);
        }

    }

    @Getter
    @RequiredArgsConstructor
    enum Style {

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
        private final boolean inline;

        public @NotNull ConcurrentList<Field> getPageItems(@NotNull Optional<Triple<String, String, String>> columnNames, @NotNull ConcurrentList<Item> items) {
            return this.getPageItems(columnNames.orElse(NOOP_HANDLER), items);
        }

        private @NotNull ConcurrentList<Field> getPageItems(@NotNull Triple<String, String, String> columnNames, @NotNull ConcurrentList<Item> items) {
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

    @Getter
    @RequiredArgsConstructor
    enum Type {

        UNKNOWN(-1, true),
        PAGE(1, true),
        AUTHOR(2, false),
        TITLE(3, false),
        DESCRIPTION(4, false),
        THUMBNAIL_URL(5, false),
        IMAGE_URL(6, false),
        FIELD(7, true),
        FOOTER(8, false);

        private final int value;
        private final boolean fieldRender;

        public static @NotNull Type of(int value) {
            return Arrays.stream(values())
                .filter(style -> style.getValue() == value)
                .findFirst()
                .orElse(UNKNOWN);
        }

    }

}
