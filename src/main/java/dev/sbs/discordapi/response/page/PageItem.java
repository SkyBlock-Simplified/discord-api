package dev.sbs.discordapi.response.page;

import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.data.tuple.Pair;
import dev.sbs.api.util.data.tuple.Triple;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.StreamUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.action.SelectMenu;
import dev.sbs.discordapi.response.embed.Field;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class PageItem {

    @Getter private final UUID uniqueId;
    @Getter private final SelectMenu.Option option;
    @Getter private final ConcurrentMap<Integer, ConcurrentList<String>> data;

    public static PageItemBuilder builder() {
        return new PageItemBuilder(UUID.randomUUID());
    }

    public static PageItemBuilder from(PageItem pageItem) {
        return new PageItemBuilder(pageItem.getUniqueId())
            .withOption(pageItem.getOption());
    }

    public ConcurrentList<String> getAllData() {
        return Stream.concat(
                Stream.concat(
                    this.getData().get(1).stream(),
                    this.getData().get(2).stream()
                ),
                this.getData().get(3).stream()
            )
            .collect(Concurrent.toList());
    }

    public ConcurrentList<String> getColumn(int column) {
        column = Math.max(1, Math.min(3, column));
        return this.getData().get(column);
    }

    public String getFieldName() {
        return FormatUtil.format(
            "{0}{1}",
            this.getOption()
                .getEmoji()
                .map(Emoji::asSpacedFormat)
                .orElse(""),
            this.getOption()
                .getLabel()
        );
    }

    public PageItemBuilder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class PageItemBuilder implements Builder<PageItem> {

        private final UUID uniqueId;
        private final SelectMenu.Option.OptionBuilder optionBuilder = SelectMenu.Option.builder();
        private final ConcurrentMap<Integer, ConcurrentList<String>> data = Concurrent.newMap(
            Pair.of(1, Concurrent.newList()),
            Pair.of(2, Concurrent.newList()),
            Pair.of(3, Concurrent.newList())
        );

        public PageItemBuilder clearData() {
            this.data.get(1).clear();
            this.data.get(2).clear();
            this.data.get(3).clear();
            return this;
        }

        public PageItemBuilder clearData(int column) {
            this.data.get(column).clear();
            return this;
        }

        public PageItemBuilder withData(@NotNull String data) {
            return this.withData(data, 1);
        }

        public PageItemBuilder withData(@NotNull String data, int column) {
            column = Math.max(1, Math.min(3, column));
            this.data.get(column).add(data);
            return this;
        }

        public PageItemBuilder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        public PageItemBuilder withEmoji(@NotNull Optional<Emoji> emoji) {
            this.optionBuilder.withEmoji(emoji);
            return this;
        }

        public PageItemBuilder withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        public PageItemBuilder withDescription(@NotNull Optional<String> description) {
            this.optionBuilder.withDescription(description);
            return this;
        }

        public PageItemBuilder withLabel(@NotNull String label) {
            this.optionBuilder.withLabel(label);
            return this;
        }

        public PageItemBuilder withOption(@NotNull SelectMenu.Option option) {
            return this.withEmoji(option.getEmoji())
                .withDescription(option.getDescription())
                .withLabel(option.getLabel())
                .withValue(option.getValue());
        }

        public PageItemBuilder withValue(@NotNull String value) {
            this.optionBuilder.withValue(value);
            return this;
        }

        @Override
        public PageItem build() {
            return new PageItem(
                this.uniqueId,
                this.optionBuilder.build(),
                this.data
            );
        }

    }

    @RequiredArgsConstructor
    public enum Style {

        FIELD(getFieldConverter(false)),
        FIELD_INLINE(getFieldConverter(true)),
        LIST(fieldNames -> pageItems -> Concurrent.newList(
            Field.builder()
                .withName(fieldNames.getLeft())
                .withValue(
                    pageItems.stream()
                        .map(pageItem -> pageItem.getColumn(1).getOrDefault(0, ""))
                        .collect(StreamUtil.toStringBuilder(true))
                        .build()
                )
                .isInline()
                .build(),
            Field.builder()
                .withName(fieldNames.getMiddle())
                .withValue(
                    pageItems.stream()
                        .map(pageItem -> pageItem.getColumn(2).getOrDefault(0, ""))
                        .collect(StreamUtil.toStringBuilder(true))
                        .build()
                )
                .isInline()
                .build(),
            Field.builder()
                .withName(fieldNames.getRight())
                .withValue(
                    pageItems.stream()
                        .map(pageItem -> pageItem.getColumn(3).getOrDefault(0, ""))
                        .collect(StreamUtil.toStringBuilder(true))
                        .build()
                )
                .isInline()
                .build()
        )),
        SINGLE_COLUMN(fieldNames -> pageItems -> Concurrent.newList(
            Field.builder()
                .withName(fieldNames.getLeft())
                .withValue(
                    pageItems.stream()
                        .map(PageItem::getFieldName)
                        .collect(StreamUtil.toStringBuilder(true))
                        .build()
                )
                .build()
        )),
        TABLE(getTableConverter(pageItem -> StringUtil.join(pageItem.getColumn(1), "\n"))),
        TABLE_DESCRIPTION(getTableConverter(pageItem -> pageItem.getOption().getDescription().orElse("")));

        private static final Triple<String, String, String> NOOP_HANDLER = Triple.of(null, null, null);
        private final Function<Triple<String, String, String>, Function<ConcurrentList<PageItem>, ConcurrentList<Field>>> converter;

        public Function<ConcurrentList<PageItem>, ConcurrentList<Field>> getConverter() {
            return this.getConverter(Optional.empty());
        }

        public Function<ConcurrentList<PageItem>, ConcurrentList<Field>> getConverter(@Nullable Triple<String, String, String> fieldNameConverter) {
            return this.getConverter(Optional.ofNullable(fieldNameConverter));
        }

        public Function<ConcurrentList<PageItem>, ConcurrentList<Field>> getConverter(@NotNull Optional<Triple<String, String, String>> fieldNameConverter) {
            return this.converter.apply(fieldNameConverter.orElse(NOOP_HANDLER));
        }

        private static Function<Triple<String, String, String>, Function<ConcurrentList<PageItem>, ConcurrentList<Field>>> getTableConverter(Function<PageItem, String> firstColumnConverter) {
            return fieldNames -> pageItems -> pageItems.stream()
                .flatMap(pageItem -> Concurrent.newList(
                    Field.builder()
                        .withName(Optional.ofNullable(StringUtil.defaultIfEmpty(fieldNames.getLeft(), null)).orElse(pageItem.getFieldName()))
                        .withValue(firstColumnConverter.apply(pageItem))
                        .isInline()
                        .build(),
                    Field.builder()
                        .withName(fieldNames.getMiddle())
                        .withValue(StringUtil.join(pageItem.getColumn(2), "\n"))
                        .isInline()
                        .build(),
                    Field.builder()
                        .withName(fieldNames.getRight())
                        .withValue(StringUtil.join(pageItem.getColumn(3), "\n"))
                        .isInline()
                        .build()
                ).stream())
                .collect(Concurrent.toList());
        }

        private static Function<Triple<String, String, String>, Function<ConcurrentList<PageItem>, ConcurrentList<Field>>> getFieldConverter(boolean inline) {
            return fieldNames -> pageItems -> pageItems.stream()
                .map(pageItem -> Field.builder()
                    .withName(pageItem.getFieldName())
                    .withValue(
                        FormatUtil.format(
                            "{0}{1}",
                            pageItem.getOption()
                                .getDescription()
                                .map(value -> FormatUtil.format("{0}\n\n", value))
                                .orElse(""),
                            StringUtil.join(pageItem.getAllData(), "\n")
                        )
                    )
                    .isInline(inline)
                    .build()
                )
                .collect(Concurrent.toList());
        }

    }

}
