package dev.sbs.discordapi.response.component.action;

import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.message.interaction.component.ComponentContext;
import dev.sbs.discordapi.context.message.interaction.component.button.ButtonContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.util.DiscordObject;
import discord4j.core.object.reaction.ReactionEmoji;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Button extends ActionComponent<ButtonContext, Function<ButtonContext, Mono<Void>>> {

    private static final Function<ButtonContext, Mono<Void>> NOOP_HANDLER = ComponentContext::deferEdit;
    @Getter private final @NotNull UUID uniqueId;
    @Getter private final @NotNull Style style;
    @Getter private final boolean disabled;
    @Getter private final @NotNull Optional<Emoji> emoji;
    @Getter private final @NotNull Optional<String> label;
    @Getter private final @NotNull Optional<String> url;
    @Getter private final boolean preserved;
    @Getter private final boolean deferEdit;
    @Getter private final @NotNull PageType pageType;
    private final @NotNull Optional<Function<ButtonContext, Mono<Void>>> buttonInteraction;

    @Override
    public Function<ButtonContext, Mono<Void>> getInteraction() {
        return this.buttonInteraction.orElse(NOOP_HANDLER);
    }

    public static ButtonBuilder builder() {
        return new ButtonBuilder(UUID.randomUUID());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        Button button = (Button) o;

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

    @Override
    public boolean isPaging() {
        return this.getPageType() != PageType.NONE;
    }

    public ButtonBuilder mutate() {
        return new ButtonBuilder(this.getUniqueId())
            .withStyle(this.getStyle())
            .setDisabled(this.isDisabled())
            .isPreserved(this.isPreserved())
            .withPageType(this.getPageType())
            .withEmoji(this.getEmoji())
            .withLabel(this.getLabel())
            .withUrl(this.getUrl())
            .onInteract(this.buttonInteraction);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ButtonBuilder implements Builder<Button> {

        private final UUID uniqueId;
        private Style style = Style.UNKNOWN;
        private boolean disabled;
        private boolean preserved;
        private boolean deferEdit;
        private PageType pageType = PageType.NONE;
        private Optional<Function<ButtonContext, Mono<Void>>> interaction = Optional.empty();
        private Optional<Emoji> emoji = Optional.empty();
        private Optional<String> label = Optional.empty();
        private Optional<String> url = Optional.empty();

        /**
         * Sets this {@link Button} as preserved when a {@link Response} is removed from {@link DiscordBot#getResponseCache()}.
         */
        public ButtonBuilder isPreserved() {
            return this.isPreserved(true);
        }

        /**
         * Sets whether to preserve this {@link Button} when a {@link Response} is removed from {@link DiscordBot#getResponseCache()}.
         *
         * @param preserved True to preserve this button.
         */
        public ButtonBuilder isPreserved(boolean preserved) {
            this.preserved = preserved;
            return this;
        }

        /**
         * Sets the interaction to execute when the {@link Button} is interacted with by a user.
         *
         * @param interaction The interaction consumer.
         */
        public ButtonBuilder onInteract(@Nullable Function<ButtonContext, Mono<Void>> interaction) {
            return this.onInteract(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction to execute when the {@link Button} is interacted with by a user.
         *
         * @param interaction The interaction consumer.
         */
        public ButtonBuilder onInteract(@NotNull Optional<Function<ButtonContext, Mono<Void>>> interaction) {
            this.interaction = interaction;
            return this;
        }

        /**
         * Sets the {@link Button} as enabled.
         */
        public ButtonBuilder setEnabled() {
            return this.setEnabled(false);
        }

        /**
         * Sets if the {@link Button} should be enabled.
         *
         * @param enabled True to enable the button.
         */
        public ButtonBuilder setEnabled(boolean enabled) {
            return this.setDisabled(!enabled);
        }

        /**
         * Sets the {@link Button} as disabled.
         */
        public ButtonBuilder setDisabled() {
            return this.setDisabled(true);
        }

        /**
         * Sets if the {@link Button} should be disabled.
         *
         * @param disabled True to disable the button.
         */
        public ButtonBuilder setDisabled(boolean disabled) {
            this.disabled = disabled;
            return this;
        }

        /**
         * Sets this {@link Button} as deferred when interacting.
         */
        public ButtonBuilder withDeferEdit() {
            return this.withDeferEdit(true);
        }

        /**
         * Sets whether this {@link Button} is deferred when interacting.
         *
         * @param deferEdit True to defer interaction.
         */
        public ButtonBuilder withDeferEdit(boolean deferEdit) {
            this.deferEdit = deferEdit;
            return this;
        }

        /**
         * Sets the label text of the {@link Button}.
         *
         * @param label The label of the button.
         */
        public ButtonBuilder withLabel(@Nullable String label) {
            return this.withLabel(Optional.ofNullable(label));
        }

        /**
         * Sets the label text of the {@link Button}.
         *
         * @param label The label of the button.
         */
        public ButtonBuilder withLabel(Optional<String> label) {
            this.label = label;
            return this;
        }

        /**
         * Sets the {@link Emoji} used in the {@link Button}.
         *
         * @param emoji The emoji of the button.
         */
        public ButtonBuilder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the {@link Emoji} used in the {@link Button}.
         *
         * @param emoji The emoji of the button.
         */
        public ButtonBuilder withEmoji(@NotNull Optional<Emoji> emoji) {
            this.emoji = emoji;
            return this;
        }

        /**
         * Sets the page type of the {@link Button}.
         *
         * @param pageType The page type of the button.
         */
        public ButtonBuilder withPageType(@NotNull PageType pageType) {
            this.pageType = pageType;
            return this;
        }

        /**
         * Sets the {@link Style} of the {@link Button}.
         *
         * @param style The style of the button.
         */
        public ButtonBuilder withStyle(@NotNull Style style) {
            this.style = style;
            return this;
        }

        /**
         * Sets the {@link Button} url for a given LINK {@link Style}.
         *
         * @param url The url to open.
         */
        public ButtonBuilder withUrl(@Nullable String url) {
            return this.withUrl(Optional.ofNullable(url));
        }

        /**
         * Sets the {@link Button} url for a given LINK {@link Style}.
         *
         * @param url The url to open.
         */
        public ButtonBuilder withUrl(Optional<String> url) {
            this.url = url;
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link SelectMenu} component.
         */
        @Override
        public Button build() {
            return new Button(
                this.uniqueId,
                this.style,
                this.disabled,
                this.emoji,
                this.label,
                this.url,
                this.preserved,
                this.deferEdit,
                this.pageType,
                this.interaction
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
        FIRST("First", true, buttonBuilder -> buttonBuilder.setDisabled(true), DiscordObject.getEmoji("ARROW_SQUARE_FIRST")),
        PREVIOUS("Previous", true, buttonBuilder -> buttonBuilder.setDisabled(true), DiscordObject.getEmoji("ARROW_SQUARE_PREVIOUS")),
        INDEX("Index", true, buttonBuilder -> buttonBuilder.setDisabled(true)),
        NEXT("Next", true, buttonBuilder -> buttonBuilder.setDisabled(false), DiscordObject.getEmoji("ARROW_SQUARE_NEXT")),
        LAST("Last", true, buttonBuilder -> buttonBuilder.setDisabled(false), DiscordObject.getEmoji("ARROW_SQUARE_LAST")),
        BACK("Back", false, buttonBuilder -> buttonBuilder.setDisabled(true), DiscordObject.getEmoji("ARROW_LEFT"));

        @Getter private final @NotNull String label;
        @Getter private final @NotNull Optional<Emoji> emoji;
        @Getter private final boolean forItemList;
        @Getter private final Function<ButtonBuilder, ButtonBuilder> defaultBuilder;

        PageType(@NotNull String label, boolean forItemList) {
            this(label, forItemList, __ -> __);
        }

        PageType(@NotNull String label, boolean forItemList, Function<ButtonBuilder, ButtonBuilder> defaultBuilder) {
            this(label, forItemList, defaultBuilder, Optional.empty());
        }

        PageType(@NotNull String label, boolean forItemList, Function<ButtonBuilder, ButtonBuilder> defaultBuilder, @Nullable Emoji emoji) {
            this(label, forItemList, defaultBuilder, Optional.ofNullable(emoji));
        }

        PageType(@NotNull String label, boolean forItemList, Function<ButtonBuilder, ButtonBuilder> defaultBuilder, @NotNull Optional<Emoji> emoji) {
            this.label = label;
            this.emoji = emoji;
            this.forItemList = forItemList;
            this.defaultBuilder = defaultBuilder;
        }

        public Button build() {
            return this.getDefaultBuilder().apply(
                Button.builder()
                    .withStyle(Button.Style.SECONDARY)
                    .withEmoji(this.getEmoji())
                    .withLabel(this.getLabel())
                    .withPageType(this)
            ).build();
        }

    }

}
