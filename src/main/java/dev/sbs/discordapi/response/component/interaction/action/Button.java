package dev.sbs.discordapi.response.component.interaction.action;

import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.interaction.deferrable.component.ComponentContext;
import dev.sbs.discordapi.context.interaction.deferrable.component.action.button.ButtonContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.type.InteractableComponent;
import dev.sbs.discordapi.response.component.type.PreservableComponent;
import dev.sbs.discordapi.util.base.DiscordHelper;
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
public final class Button extends ActionComponent implements InteractableComponent<ButtonContext>, PreservableComponent {

    private static final Function<ButtonContext, Mono<Void>> NOOP_HANDLER = ComponentContext::deferEdit;
    @Getter private final @NotNull String identifier;
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
    public @NotNull Function<ButtonContext, Mono<Void>> getInteraction() {
        return this.buttonInteraction.orElse(NOOP_HANDLER);
    }

    public static ButtonBuilder builder() {
        return new ButtonBuilder().withIdentifier(UUID.randomUUID().toString());
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

    public static ButtonBuilder from(@NotNull Button button) {
        return new ButtonBuilder()
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
    public discord4j.core.object.component.Button getD4jComponent() {
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

    public ButtonBuilder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ButtonBuilder implements Builder<Button> {

        private String identifier;
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
         * @param interaction The interaction function.
         */
        public ButtonBuilder onInteract(@Nullable Function<ButtonContext, Mono<Void>> interaction) {
            return this.onInteract(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction to execute when the {@link Button} is interacted with by a user.
         *
         * @param interaction The interaction function.
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
         * Overrides the default identifier of the {@link Button}.
         *
         * @param identifier The identifier to use.
         * @param objects The objects used to format the identifier.
         */
        public ButtonBuilder withIdentifier(@NotNull String identifier, @NotNull Object... objects) {
            this.identifier = FormatUtil.format(identifier, objects);
            return this;
        }

        /**
         * Sets the label text of the {@link Button}.
         *
         * @param label The label of the button.
         * @param objects The objects used to format the url.
         */
        public ButtonBuilder withLabel(@Nullable String label, @NotNull Object... objects) {
            return this.withLabel(FormatUtil.formatNullable(label, objects));
        }

        /**
         * Sets the label text of the {@link Button}.
         *
         * @param label The label of the button.
         */
        public ButtonBuilder withLabel(@NotNull Optional<String> label) {
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
         * @param objects The objects used to format the url.
         */
        public ButtonBuilder withUrl(@Nullable String url, @NotNull Object... objects) {
            return this.withUrl(FormatUtil.formatNullable(url, objects));
        }

        /**
         * Sets the {@link Button} url for a given LINK {@link Style}.
         *
         * @param url The url to open.
         */
        public ButtonBuilder withUrl(@NotNull Optional<String> url) {
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
                this.identifier,
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

    @AllArgsConstructor
    public enum PageType {

        NONE("", -1),
        FIRST("First", 1, DiscordHelper.getEmoji("ARROW_SQUARE_FIRST")),
        PREVIOUS("Previous", 1, DiscordHelper.getEmoji("ARROW_SQUARE_PREVIOUS")),
        INDEX("Index", 1),
        NEXT("Next", 1, DiscordHelper.getEmoji("ARROW_SQUARE_NEXT")),
        LAST("Last", 1, DiscordHelper.getEmoji("ARROW_SQUARE_LAST")),
        BACK("Back", -1, DiscordHelper.getEmoji("ARROW_LEFT")),
        SEARCH("Search", 2, DiscordHelper.getEmoji("SEARCH")),
        SORT("Sort: {0}", 2, DiscordHelper.getEmoji("SORT")),
        ORDER("Order: {0}", 2, DiscordHelper.getEmoji("SORT_DESCENDING"));

        @Getter private final @NotNull String label;
        @Getter private final int row;
        @Getter private final @NotNull Optional<Emoji> emoji;

        PageType(@NotNull String label, int row) {
            this(label, row, Optional.empty());
        }

        public Button build() {
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
