package dev.sbs.discordapi.response.component.interaction.action;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.builder.annotation.BuildFlag;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.deferrable.component.ComponentContext;
import dev.sbs.discordapi.context.deferrable.component.action.ButtonContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.type.InteractableComponent;
import dev.sbs.discordapi.response.component.type.PreservableComponent;
import dev.sbs.discordapi.util.DiscordReference;
import discord4j.core.object.reaction.ReactionEmoji;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Button extends ActionComponent implements InteractableComponent<ButtonContext>, PreservableComponent {

    private static final Function<ButtonContext, Mono<Void>> NOOP_HANDLER = ComponentContext::deferEdit;
    private final @NotNull String identifier;
    private final @NotNull Style style;
    private final boolean disabled;
    private final @NotNull Optional<Emoji> emoji;
    private final @NotNull Optional<String> label;
    private final @NotNull Optional<String> url;
    private final boolean preserved;
    private final boolean deferEdit;
    private final @NotNull PageType pageType;
    private final @NotNull Function<ButtonContext, Mono<Void>> interaction;

    public static @NotNull Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        Button button = (Button) o;

        return new EqualsBuilder()
            .append(this.getIdentifier(), button.getIdentifier())
            .append(this.getStyle(), button.getStyle())
            .append(this.isDisabled(), button.isDisabled())
            .append(this.getEmoji(), button.getEmoji())
            .append(this.getLabel(), button.getLabel())
            .append(this.getUrl(), button.getUrl())
            .append(this.isPreserved(), button.isPreserved())
            .append(this.isDeferEdit(), button.isDeferEdit())
            .append(this.getPageType(), button.getPageType())
            .build();
    }

    public static @NotNull Builder from(@NotNull Button button) {
        return new Builder()
            .withIdentifier(button.getIdentifier())
            .withStyle(button.getStyle())
            .setDisabled(button.isDisabled())
            .withEmoji(button.getEmoji())
            .withLabel(button.getLabel())
            .withUrl(button.getUrl())
            .isPreserved(button.isPreserved())
            .withDeferEdit(button.isDeferEdit())
            .withPageType(button.getPageType());
    }

    @Override
    public @NotNull discord4j.core.object.component.Button getD4jComponent() {
        ReactionEmoji d4jReaction = this.getEmoji().map(Emoji::getD4jReaction).orElse(null);
        String label = this.getLabel().orElse(null);

        return (switch (this.getStyle()) {
            case PRIMARY -> discord4j.core.object.component.Button.primary(this.getIdentifier(), d4jReaction, label);
            case SUCCESS -> discord4j.core.object.component.Button.success(this.getIdentifier(), d4jReaction, label);
            case DANGER -> discord4j.core.object.component.Button.danger(this.getIdentifier(), d4jReaction, label);
            case LINK -> discord4j.core.object.component.Button.link(this.getUrl().orElse(""), d4jReaction, label);
            case SECONDARY, UNKNOWN -> discord4j.core.object.component.Button.secondary(this.getIdentifier(), d4jReaction, label);
        }).disabled(this.isDisabled());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getIdentifier())
            .append(this.getStyle())
            .append(this.isDisabled())
            .append(this.getEmoji())
            .append(this.getLabel())
            .append(this.getUrl())
            .append(this.isPreserved())
            .append(this.getPageType())
            .build();
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder implements dev.sbs.api.util.builder.Builder<Button> {

        @BuildFlag(nonNull = true)
        private String identifier;
        @BuildFlag(nonNull = true)
        private Style style = Style.UNKNOWN;
        private boolean disabled;
        private boolean preserved;
        private boolean deferEdit;
        @BuildFlag(nonNull = true)
        private PageType pageType = PageType.NONE;
        private Optional<Function<ButtonContext, Mono<Void>>> interaction = Optional.empty();
        @BuildFlag(nonNull = true, group = "face")
        private Optional<Emoji> emoji = Optional.empty();
        @BuildFlag(nonNull = true, group = "face")
        private Optional<String> label = Optional.empty();
        private Optional<String> url = Optional.empty();

        /**
         * Sets this {@link Button} as preserved when a {@link Response} is removed from {@link DiscordBot#getResponseCache()}.
         */
        public Builder isPreserved() {
            return this.isPreserved(true);
        }

        /**
         * Sets whether to preserve this {@link Button} when a {@link Response} is removed from {@link DiscordBot#getResponseCache()}.
         *
         * @param preserved True to preserve this button.
         */
        public Builder isPreserved(boolean preserved) {
            this.preserved = preserved;
            return this;
        }

        /**
         * Sets the interaction to execute when the {@link Button} is interacted with by a user.
         *
         * @param interaction The interaction function.
         */
        public Builder onInteract(@Nullable Function<ButtonContext, Mono<Void>> interaction) {
            return this.onInteract(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction to execute when the {@link Button} is interacted with by a user.
         *
         * @param interaction The interaction function.
         */
        public Builder onInteract(@NotNull Optional<Function<ButtonContext, Mono<Void>>> interaction) {
            this.interaction = interaction;
            return this;
        }

        /**
         * Sets the {@link Button} as enabled.
         */
        public Builder setEnabled() {
            return this.setEnabled(true);
        }

        /**
         * Sets if the {@link Button} should be enabled.
         *
         * @param value True to enable the button.
         */
        public Builder setEnabled(boolean value) {
            return this.setDisabled(!value);
        }

        /**
         * Sets the {@link Button} as disabled.
         */
        public Builder setDisabled() {
            return this.setDisabled(true);
        }

        /**
         * Sets if the {@link Button} should be disabled.
         *
         * @param value True to disable the button.
         */
        public Builder setDisabled(boolean value) {
            this.disabled = value;
            return this;
        }

        /**
         * Sets this {@link Button} as deferred when interacting.
         */
        public Builder withDeferEdit() {
            return this.withDeferEdit(true);
        }

        /**
         * Sets whether this {@link Button} is deferred when interacting.
         *
         * @param value True to defer interaction.
         */
        public Builder withDeferEdit(boolean value) {
            this.deferEdit = value;
            return this;
        }

        /**
         * Overrides the default identifier of the {@link Button}.
         *
         * @param identifier The identifier to use.
         */
        public Builder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Overrides the default identifier of the {@link Button}.
         *
         * @param identifier The identifier to use.
         * @param args The objects used to format the identifier.
         */
        public Builder withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
            this.identifier = String.format(identifier, args);
            return this;
        }

        /**
         * Sets the label text of the {@link Button}.
         *
         * @param label The label of the button.
         */
        public Builder withLabel(@Nullable String label) {
            return this.withLabel(Optional.ofNullable(label));
        }

        /**
         * Sets the label text of the {@link Button}.
         *
         * @param label The label of the button.
         * @param args The objects used to format the url.
         */
        public Builder withLabel(@PrintFormat @Nullable String label, @Nullable Object... args) {
            return this.withLabel(StringUtil.formatNullable(label, args));
        }

        /**
         * Sets the label text of the {@link Button}.
         *
         * @param label The label of the button.
         */
        public Builder withLabel(@NotNull Optional<String> label) {
            this.label = label;
            return this;
        }

        /**
         * Sets the {@link Emoji} used in the {@link Button}.
         *
         * @param emoji The emoji of the button.
         */
        public Builder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the {@link Emoji} used in the {@link Button}.
         *
         * @param emoji The emoji of the button.
         */
        public Builder withEmoji(@NotNull Optional<Emoji> emoji) {
            this.emoji = emoji;
            return this;
        }

        /**
         * Sets the page type of the {@link Button}.
         *
         * @param pageType The page type of the button.
         */
        public Builder withPageType(@NotNull PageType pageType) {
            this.pageType = pageType;
            return this;
        }

        /**
         * Sets the {@link Style} of the {@link Button}.
         *
         * @param style The style of the button.
         */
        public Builder withStyle(@NotNull Style style) {
            this.style = style;
            return this;
        }

        /**
         * Sets the {@link Button} url for a given LINK {@link Style}.
         *
         * @param url The url to open.
         */
        public Builder withUrl(@Nullable String url) {
            return this.withUrl(Optional.ofNullable(url));
        }

        /**
         * Sets the {@link Button} url for a given LINK {@link Style}.
         *
         * @param url The url to open.
         * @param args The objects used to format the url.
         */
        public Builder withUrl(@PrintFormat @Nullable String url, @Nullable Object... args) {
            return this.withUrl(StringUtil.formatNullable(url, args));
        }

        /**
         * Sets the {@link Button} url for a given LINK {@link Style}.
         *
         * @param url The url to open.
         */
        public Builder withUrl(@NotNull Optional<String> url) {
            this.url = url;
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link SelectMenu} component.
         */
        @Override
        public @NotNull Button build() {
            Reflection.validateFlags(this);

            return new Button(
                this.identifier,
                this.style,
                this.disabled,
                this.emoji,
                this.label,
                this.url,
                this.preserved,
                this.deferEdit,
                this.pageType,
                this.interaction.orElse(NOOP_HANDLER)
            );
        }

    }

    @Getter
    @RequiredArgsConstructor
    public enum Style {

        UNKNOWN(-1),
        /**
         * Blue
         */
        PRIMARY(1),
        /**
         * Gray
         */
        SECONDARY(2),
        /**
         * Green
         */
        SUCCESS(3),
        /**
         * Red
         */
        DANGER(4),
        LINK(5);

        /**
         * The Discord Button Integer value for this style.
         */
        private final int value;

        public static @NotNull Style of(int value) {
            return Arrays.stream(values())
                .filter(style -> style.getValue() == value)
                .findFirst()
                .orElse(UNKNOWN);
        }

    }

    @Getter
    @RequiredArgsConstructor
    public enum PageType {

        NONE("", -1),
        FIRST("First", 1, DiscordReference.getEmoji("ARROW_SQUARE_FIRST")),
        PREVIOUS("Previous", 1, DiscordReference.getEmoji("ARROW_SQUARE_PREVIOUS")),
        INDEX("Index", 1),
        NEXT("Next", 1, DiscordReference.getEmoji("ARROW_SQUARE_NEXT")),
        LAST("Last", 1, DiscordReference.getEmoji("ARROW_SQUARE_LAST")),
        BACK("Back", 2, DiscordReference.getEmoji("ARROW_LEFT")),
        SEARCH("Search", 2, DiscordReference.getEmoji("SEARCH")),
        SORT("Sort", 2, DiscordReference.getEmoji("SORT")),
        ORDER("Order", 2, DiscordReference.getEmoji("SORT_DESCENDING"));

        private final @NotNull String label;
        private final int row;
        private final @NotNull Optional<Emoji> emoji;

        PageType(@NotNull String label, int row) {
            this(label, row, Optional.empty());
        }

        public @NotNull Button build() {
            return Button.builder()
                .withStyle(Button.Style.SECONDARY)
                .withEmoji(this.getEmoji())
                .withLabel(this.getLabel())
                .withPageType(this)
                .setDisabled(true)
                .build();
        }

        public static int getNumberOfRows() {
            return Arrays.stream(values())
                .mapToInt(PageType::getRow)
                .max()
                .orElse(1);
        }

    }

}
