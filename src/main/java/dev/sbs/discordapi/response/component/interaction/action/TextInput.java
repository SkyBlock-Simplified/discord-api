package dev.sbs.discordapi.response.component.interaction.action;

import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.util.base.DiscordHelper;
import discord4j.core.object.reaction.ReactionEmoji;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextInput extends ActionComponent {

    @Getter private final @NotNull UUID uniqueId;
    @Getter private final @NotNull Style style;
    @Getter private final boolean disabled;
    @Getter private final @NotNull Optional<Emoji> emoji;
    @Getter private final @NotNull Optional<String> label;
    @Getter private final @NotNull Optional<String> url;
    @Getter private final boolean preserved;
    @Getter private final boolean deferEdit;
    @Getter private final @NotNull PageType pageType;

    public static TextInputBuilder builder() {
        return new TextInputBuilder(UUID.randomUUID());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        TextInput button = (TextInput) o;

        return new EqualsBuilder()
            .append(this.isDisabled(), button.isDisabled())
            .append(this.isPreserved(), button.isPreserved())
            .append(this.getStyle(), button.getStyle())
            .append(this.getEmoji(), button.getEmoji())
            .append(this.getLabel(), button.getLabel())
            .append(this.getUrl(), button.getUrl())
            .append(this.getPageType(), button.getPageType())
            .build();
    }

    @Override
    public discord4j.core.object.component.Button getD4jComponent() {
        ReactionEmoji d4jReaction = this.getEmoji().map(Emoji::getD4jReaction).orElse(null);
        String label = this.getLabel().orElse(null);

        return (switch (this.getStyle()) {
            case PRIMARY -> discord4j.core.object.component.Button.primary(this.getUniqueId().toString(), d4jReaction, label);
            case SUCCESS -> discord4j.core.object.component.Button.success(this.getUniqueId().toString(), d4jReaction, label);
            case DANGER -> discord4j.core.object.component.Button.danger(this.getUniqueId().toString(), d4jReaction, label);
            case LINK -> discord4j.core.object.component.Button.link(this.getUrl().orElse(""), d4jReaction, label);
            case SECONDARY, UNKNOWN -> discord4j.core.object.component.Button.secondary(this.getUniqueId().toString(), d4jReaction, label);
        }).disabled(this.isDisabled());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(this.getStyle())
            .append(this.isDisabled())
            .append(this.getEmoji())
            .append(this.getLabel())
            .append(this.getUrl())
            .append(this.isPreserved())
            .append(this.getPageType())
            .build();
    }

    public TextInputBuilder mutate() {
        return new TextInputBuilder(this.getUniqueId())
            .withStyle(this.getStyle())
            .setDisabled(this.isDisabled())
            .isPreserved(this.isPreserved())
            .withPageType(this.getPageType())
            .withEmoji(this.getEmoji())
            .withLabel(this.getLabel())
            .withUrl(this.getUrl());
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class TextInputBuilder implements Builder<TextInput> {

        private final UUID uniqueId;
        private Style style = Style.UNKNOWN;
        private boolean disabled;
        private boolean preserved;
        private boolean deferEdit;
        private PageType pageType = PageType.NONE;
        private Optional<Emoji> emoji = Optional.empty();
        private Optional<String> label = Optional.empty();
        private Optional<String> url = Optional.empty();

        /**
         * Sets this {@link TextInput} as preserved when a {@link Response} is removed from {@link DiscordBot#getResponseCache()}.
         */
        public TextInputBuilder isPreserved() {
            return this.isPreserved(true);
        }

        /**
         * Sets whether to preserve this {@link TextInput} when a {@link Response} is removed from {@link DiscordBot#getResponseCache()}.
         *
         * @param preserved True to preserve this button.
         */
        public TextInputBuilder isPreserved(boolean preserved) {
            this.preserved = preserved;
            return this;
        }

        /**
         * Sets the {@link TextInput} as enabled.
         */
        public TextInputBuilder setEnabled() {
            return this.setEnabled(false);
        }

        /**
         * Sets if the {@link TextInput} should be enabled.
         *
         * @param enabled True to enable the button.
         */
        public TextInputBuilder setEnabled(boolean enabled) {
            return this.setDisabled(!enabled);
        }

        /**
         * Sets the {@link TextInput} as disabled.
         */
        public TextInputBuilder setDisabled() {
            return this.setDisabled(true);
        }

        /**
         * Sets if the {@link TextInput} should be disabled.
         *
         * @param disabled True to disable the button.
         */
        public TextInputBuilder setDisabled(boolean disabled) {
            this.disabled = disabled;
            return this;
        }

        /**
         * Sets this {@link TextInput} as deferred when interacting.
         */
        public TextInputBuilder withDeferEdit() {
            return this.withDeferEdit(true);
        }

        /**
         * Sets whether this {@link TextInput} is deferred when interacting.
         *
         * @param deferEdit True to defer interaction.
         */
        public TextInputBuilder withDeferEdit(boolean deferEdit) {
            this.deferEdit = deferEdit;
            return this;
        }

        /**
         * Sets the label text of the {@link TextInput}.
         *
         * @param label The label of the button.
         */
        public TextInputBuilder withLabel(@Nullable String label) {
            return this.withLabel(Optional.ofNullable(label));
        }

        /**
         * Sets the label text of the {@link TextInput}.
         *
         * @param label The label of the button.
         */
        public TextInputBuilder withLabel(@NotNull Optional<String> label) {
            this.label = label;
            return this;
        }

        /**
         * Sets the {@link Emoji} used in the {@link TextInput}.
         *
         * @param emoji The emoji of the button.
         */
        public TextInputBuilder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the {@link Emoji} used in the {@link TextInput}.
         *
         * @param emoji The emoji of the button.
         */
        public TextInputBuilder withEmoji(@NotNull Optional<Emoji> emoji) {
            this.emoji = emoji;
            return this;
        }

        /**
         * Sets the page type of the {@link TextInput}.
         *
         * @param pageType The page type of the button.
         */
        public TextInputBuilder withPageType(@NotNull PageType pageType) {
            this.pageType = pageType;
            return this;
        }

        /**
         * Sets the {@link Style} of the {@link TextInput}.
         *
         * @param style The style of the button.
         */
        public TextInputBuilder withStyle(@NotNull Style style) {
            this.style = style;
            return this;
        }

        /**
         * Sets the {@link TextInput} url for a given LINK {@link Style}.
         *
         * @param url The url to open.
         */
        public TextInputBuilder withUrl(@Nullable String url) {
            return this.withUrl(Optional.ofNullable(url));
        }

        /**
         * Sets the {@link TextInput} url for a given LINK {@link Style}.
         *
         * @param url The url to open.
         */
        public TextInputBuilder withUrl(@NotNull Optional<String> url) {
            this.url = url;
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link SelectMenu} component.
         */
        @Override
        public TextInput build() {
            return new TextInput(
                this.uniqueId,
                this.style,
                this.disabled,
                this.emoji,
                this.label,
                this.url,
                this.preserved,
                this.deferEdit,
                this.pageType
            );
        }

    }

    @RequiredArgsConstructor
    public enum Style {

        UNKNOWN(-1),
        PRIMARY(1),
        SECONDARY(2),
        SUCCESS(3),
        DANGER(4),
        LINK(5);

        /**
         * The Discord Button Integer value for this style.
         */
        @Getter private final int value;

        public static Style of(int value) {
            return Arrays.stream(values()).filter(style -> style.getValue() == value).findFirst().orElse(UNKNOWN);
        }

    }

    @RequiredArgsConstructor
    public enum PageType {

        NONE("", false),
        FIRST("First", true, buttonBuilder -> buttonBuilder.setDisabled(true), DiscordHelper.getEmoji("ARROW_SQUARE_FIRST")),
        PREVIOUS("Previous", true, buttonBuilder -> buttonBuilder.setDisabled(true), DiscordHelper.getEmoji("ARROW_SQUARE_PREVIOUS")),
        INDEX("Index", true, buttonBuilder -> buttonBuilder.setDisabled(true)),
        NEXT("Next", true, buttonBuilder -> buttonBuilder.setDisabled(true), DiscordHelper.getEmoji("ARROW_SQUARE_NEXT")),
        LAST("Last", true, buttonBuilder -> buttonBuilder.setDisabled(true), DiscordHelper.getEmoji("ARROW_SQUARE_LAST")),
        BACK("Back", false, buttonBuilder -> buttonBuilder.setDisabled(true), DiscordHelper.getEmoji("ARROW_LEFT"));

        @Getter private final @NotNull String label;
        @Getter private final @NotNull Optional<Emoji> emoji;
        @Getter private final boolean forItemList;
        @Getter private final Function<TextInputBuilder, TextInputBuilder> defaultBuilder;

        PageType(@NotNull String label, boolean forItemList) {
            this(label, forItemList, __ -> __);
        }

        PageType(@NotNull String label, boolean forItemList, Function<TextInputBuilder, TextInputBuilder> defaultBuilder) {
            this(label, forItemList, defaultBuilder, Optional.empty());
        }

        PageType(@NotNull String label, boolean forItemList, Function<TextInputBuilder, TextInputBuilder> defaultBuilder, @Nullable Emoji emoji) {
            this(label, forItemList, defaultBuilder, Optional.ofNullable(emoji));
        }

        PageType(@NotNull String label, boolean forItemList, Function<TextInputBuilder, TextInputBuilder> defaultBuilder, @NotNull Optional<Emoji> emoji) {
            this.label = label;
            this.emoji = emoji;
            this.forItemList = forItemList;
            this.defaultBuilder = defaultBuilder;
        }

        public TextInput build() {
            return this.build(Optional.empty());
        }

        public TextInput build(Optional<String> label) {
            return this.getDefaultBuilder().apply(
                TextInput.builder()
                    .withStyle(TextInput.Style.SECONDARY)
                    .withEmoji(this.getEmoji())
                    .withLabel(label.orElse(this.getLabel()))
                    .withPageType(this)
            ).build();
        }

    }

}
