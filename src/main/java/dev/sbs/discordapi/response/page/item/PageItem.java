package dev.sbs.discordapi.response.page.item;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.response.page.Paging;
import dev.sbs.discordapi.response.page.handler.item.CustomItemHandler;
import dev.sbs.discordapi.response.page.handler.item.ItemHandler;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@Getter
public final class PageItem extends Item implements SingletonFieldItem, Paging<PageItem> {

    private final @NotNull ItemHandler<?> itemHandler;

    private PageItem(
        @NotNull SelectMenu.Option option,
        boolean editable,
        @NotNull ItemHandler<?> itemHandler
    ) {
        super(option, Type.PAGE, editable);
        this.itemHandler = itemHandler;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public @NotNull ConcurrentList<PageItem> getPages() {
        return this.getItemHandler()
            .getItems()
            .stream()
            .filter(PageItem.class::isInstance)
            .map(PageItem.class::cast)
            .collect(Concurrent.toList())
            .toUnmodifiableList();
    }

    @Override
    public @NotNull Field getRenderField() {
        return Field.builder()
            .withName(this.getOption().getLabel())
            .withValue("Goto page.")
            .isInline()
            .build();
    }

    public Builder mutate() {
        return new Builder()
            .isEditable(this.isEditable())
            .withItemHandler(this.getItemHandler())
            .withOption(this.getOption());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder extends ItemBuilder<PageItem> {

        private ItemHandler<?> itemHandler = CustomItemHandler.builder(Item.class).build();

        @Override
        public Builder isEditable() {
            return this.isEditable(true);
        }

        @Override
        public Builder isEditable(boolean value) {
            super.editable = value;
            return this;
        }

        @Override
        public Builder withDescription(@Nullable String description, @NotNull Object... objects) {
            return this.withDescription(StringUtil.formatNullable(description, objects));
        }

        @Override
        public Builder withDescription(@NotNull Optional<String> description) {
            super.optionBuilder.withDescription(description);
            return this;
        }

        @Override
        public Builder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        @Override
        public Builder withEmoji(@NotNull Optional<Emoji> emoji) {
            super.optionBuilder.withEmoji(emoji);
            return this;
        }

        @Override
        public Builder withIdentifier(@NotNull String value, @NotNull Object... objects) {
            super.optionBuilder.withValue(value, objects);
            return this;
        }

        /**
         * Sets the item data to be used with the {@link PageItem}.
         *
         * @param itemHandler The item data for the page.
         */
        public Builder withItemHandler(@NotNull ItemHandler<?> itemHandler) {
            this.itemHandler = itemHandler;
            return this;
        }

        @Override
        public Builder withLabel(@NotNull String label, @NotNull Object... objects) {
            super.optionBuilder.withLabel(label, objects);
            return this;
        }

        public Builder withOption(@NotNull SelectMenu.Option option) {
            return this.withIdentifier(option.getValue())
                .withDescription(option.getDescription())
                .withEmoji(option.getEmoji())
                .withLabel(option.getLabel());
        }

        @Override
        public PageItem build() {
            return new PageItem(
                super.optionBuilder.build(),
                super.editable,
                this.itemHandler
            );
        }

    }

}