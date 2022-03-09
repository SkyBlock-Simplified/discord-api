package dev.sbs.discordapi.response.page;

import dev.sbs.api.util.builder.Builder;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.action.SelectMenu;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class PageItem {

    @Getter private final UUID uniqueId;
    @Getter private final SelectMenu.Option option;

    public static PageItemBuilder builder() {
        return new PageItemBuilder(UUID.randomUUID());
    }

    public static PageItemBuilder from(PageItem pageItem) {
        return new PageItemBuilder(pageItem.getUniqueId())
            .withOption(pageItem.getOption());
    }

    public PageItemBuilder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class PageItemBuilder implements Builder<PageItem> {

        private final UUID uniqueId;
        private final SelectMenu.Option.OptionBuilder optionBuilder = SelectMenu.Option.builder();

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
                this.optionBuilder.build()
            );
        }

    }

}
