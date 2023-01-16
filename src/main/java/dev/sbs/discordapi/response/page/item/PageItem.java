package dev.sbs.discordapi.response.page.item;

import dev.sbs.api.util.builder.Builder;
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
public abstract class PageItem {

    @Getter private final @NotNull String identifier;
    @Getter private final @NotNull Optional<SelectMenu.Option> option;
    @Getter private final @NotNull Type type;
    @Getter private final boolean editable;

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

    protected static abstract class PageItemBuilder<T> implements Builder<T> {

        protected final SelectMenu.Option.OptionBuilder optionBuilder = SelectMenu.Option.builder();
        protected boolean editable;

        /**
         * Sets the {@link PageItem} as editable.
         */
        public abstract PageItemBuilder<T> isEditable();

        /**
         * Set the editable state of the {@link PageItem}.
         *
         * @param value The value of the menu item.
         */
        public abstract PageItemBuilder<T> isEditable(boolean value);

        /**
         * Sets the description of the {@link PageItem}.
         * <br><br>
         * This is used for the {@link SelectMenu.Option#getDescription()}.
         *
         * @param description The description to use.
         * @param objects The objects used to format the description.
         */
        public abstract PageItemBuilder<T> withDescription(@Nullable String description, @NotNull Object... objects);

        /**
         * Sets the description of the {@link PageItem}.
         * <br><br>
         * This is used for the {@link SelectMenu.Option#getDescription()}.
         *
         * @param description The description to use.
         */
        public abstract PageItemBuilder<T> withDescription(@NotNull Optional<String> description);

        /**
         * Sets the emoji of the {@link PageItem}.
         * <br><br>
         * This is used for the {@link SelectMenu.Option#getEmoji()} and {@link Field#getName()}.
         *
         * @param emoji The emoji to use.
         */
        public abstract PageItemBuilder<T> withEmoji(@Nullable Emoji emoji);

        /**
         * Sets the emoji of the {@link PageItem}.
         * <br><br>
         * This is used for the {@link SelectMenu.Option#getEmoji()} and {@link Field#getName()}.
         *
         * @param emoji The emoji to use.
         */
        public abstract PageItemBuilder<T> withEmoji(@NotNull Optional<Emoji> emoji);

        /**
         * Overrides the default identifier of the {@link PageItem}.
         * <br><br>
         * This is used for the {@link SelectMenu.Option#getIdentifier()} and {@link PageItem}.
         *
         * @param identifier The identifier to use.
         * @param objects The objects used to format the value.
         */
        public abstract PageItemBuilder<T> withIdentifier(@NotNull String identifier, @NotNull Object... objects);

        /**
         * Sets the label of the {@link PageItem}.
         * <br><br>
         * This is used for the {@link SelectMenu.Option}.
         *
         * @param label The label of the field item.
         * @param objects The objects used to format the label.
         */
        public abstract PageItemBuilder<T> withLabel(@NotNull String label, @NotNull Object... objects);


        /**
         * Sets the value of the {@link FieldItem}.
         * <br><br>
         * This is used for the {@link SelectMenu.Option}.
         *
         * @param value The value of the field item.
         * @param objects The objects used to format the value.
         */
        public abstract PageItemBuilder<T> withOptionValue(@NotNull String value, @NotNull Object... objects);

    }

    @RequiredArgsConstructor
    public enum Style {

        /**
         * Displays {@link FieldItem} into a single {@link Field}.
         * <br><br>
         * - Only uses column 1 data.
         * <br>
         * - Field names and emojis are handled by the {@link FieldItem}.
         */
        FIELD(false),
        /**
         * Displays {@link FieldItem} into a single inline {@link Field}.
         * <br><br>
         * - Only uses column 1 data.
         * <br>
         * Field names and emojis are handled by the {@link FieldItem}.
         */
        FIELD_INLINE(true),
        /**
         * Displays all {@link FieldItem FieldItems} in 3 columns as a list of data.
         * <br><br>
         * Field names and emojis are handled by column data specified on the {@link Page}.
         */
        LIST(true),
        /**
         * Displays all {@link FieldItem FieldItems} in 1 column as a list of data.
         * <br><br>
         * Field names and emojis are handled by column data specified on the {@link Page}.
         */
        LIST_SINGLE(false),
        /**
         * Displays {@link FieldItem}'s 3 columns across 3 {@link Field Fields}.
         * <br><br>
         * - The left field name and emoji are handled by the {@link FieldItem}.
         * <br>
         * - The left field value is provided by column 1 data specified by the {@link FieldItem}.
         * <br>
         * - The middle and right field names are handled by the column data specified on the {@link Page}.
         */
        TABLE(true),
        /**
         * Displays {@link FieldItem}'s 3 columns across 3 {@link Field Fields}.
         * <br><br>
         * - The left field name and emoji are handled by the {@link FieldItem}.
         * <br>
         * - The left field value is provided by description specified by the {@link FieldItem}.
         * <br>
         * - The middle and right field names are handled by the column data specified on the {@link Page}.
         */
        TABLE_DESCRIPTION(true);

        private static final Triple<String, String, String> NOOP_HANDLER = Triple.of(null, null, null);
        @Getter private final boolean inline;

        public ConcurrentList<Field> getPageItems(@NotNull Optional<Triple<String, String, String>> columnNames, @NotNull ConcurrentList<PageItem> pageItems) {
            return this.getPageItems(columnNames.orElse(NOOP_HANDLER), pageItems);
        }

        private ConcurrentList<Field> getPageItems(@NotNull Triple<String, String, String> columnNames, @NotNull ConcurrentList<PageItem> pageItems) {
            return switch (this) {
                case FIELD, FIELD_INLINE -> pageItems.stream()
                    .filter(pageItem -> pageItem.getType() == Type.FIELD)
                    .filter(pageItem -> pageItem.getClass().isAssignableFrom(FieldItem.class))
                    .map(FieldItem.class::cast)
                    .map(fieldItem -> Field.builder()
                        .withEmoji(fieldItem.getOption().flatMap(SelectMenu.Option::getEmoji))
                        .withName(fieldItem.getOption().map(SelectMenu.Option::getLabel))
                        .withValue(fieldItem.getFieldValue(this, PageItem.Column.ONE))
                        .isInline(this.isInline())
                        .build()
                    )
                    .collect(Concurrent.toList());
                case LIST -> Concurrent.newList(
                    Field.builder()
                        .withName(columnNames.getLeft())
                        .withValue(
                            pageItems.stream()
                                .filter(pageItem -> pageItem.getType() == Type.FIELD)
                                .filter(pageItem -> pageItem.getClass().isAssignableFrom(FieldItem.class))
                                .map(FieldItem.class::cast)
                                .map(pageItem -> pageItem.getFieldValue(this, PageItem.Column.ONE))
                                .collect(StreamUtil.toStringBuilder(true))
                                .build()
                        )
                        .isInline(this.isInline())
                        .build(),
                    Field.builder()
                        .withName(columnNames.getMiddle())
                        .withValue(
                            pageItems.stream()
                                .filter(pageItem -> pageItem.getType() == Type.FIELD)
                                .filter(pageItem -> pageItem.getClass().isAssignableFrom(FieldItem.class))
                                .map(FieldItem.class::cast)
                                .map(pageItem -> pageItem.getFieldValue(this, PageItem.Column.TWO))
                                .collect(StreamUtil.toStringBuilder(true))
                                .build()
                        )
                        .isInline(this.isInline())
                        .build(),
                    Field.builder()
                        .withName(columnNames.getRight())
                        .withValue(
                            pageItems.stream()
                                .filter(pageItem -> pageItem.getType() == Type.FIELD)
                                .filter(pageItem -> pageItem.getClass().isAssignableFrom(FieldItem.class))
                                .map(FieldItem.class::cast)
                                .map(pageItem -> pageItem.getFieldValue(this, PageItem.Column.THREE))
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
                            pageItems.stream()
                                .filter(pageItem -> pageItem.getType() == Type.FIELD)
                                .filter(pageItem -> pageItem.getClass().isAssignableFrom(FieldItem.class))
                                .map(FieldItem.class::cast)
                                .map(pageItem -> pageItem.getFieldValue(this, PageItem.Column.ONE))
                                .collect(StreamUtil.toStringBuilder(true))
                                .build()
                        )
                        .isInline(this.isInline())
                        .build()
                );
                case TABLE, TABLE_DESCRIPTION -> pageItems.stream()
                    .filter(pageItem -> pageItem.getType() == Type.FIELD)
                    .filter(pageItem -> pageItem.getClass().isAssignableFrom(FieldItem.class))
                    .map(FieldItem.class::cast)
                    .map(pageItem -> Concurrent.newList(
                        Field.builder()
                            .withEmoji(pageItem.getOption().flatMap(SelectMenu.Option::getEmoji))
                            .withName(pageItem.getOption().map(SelectMenu.Option::getLabel))
                            .withValue(pageItem.getFieldValue(this, PageItem.Column.ONE))
                            .isInline()
                            .build(),
                        Field.builder()
                            .withName(columnNames.getMiddle())
                            .withValue(pageItem.getFieldValue(this, PageItem.Column.TWO))
                            .isInline()
                            .build(),
                        Field.builder()
                            .withName(columnNames.getRight())
                            .withValue(pageItem.getFieldValue(this, PageItem.Column.THREE))
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
