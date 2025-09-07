package dev.sbs.discordapi.response.component.interaction.action;

import dev.sbs.api.builder.ClassBuilder;
import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import dev.sbs.api.builder.annotation.BuildFlag;
import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.StringUtil;
import dev.sbs.discordapi.context.deferrable.component.ComponentContext;
import dev.sbs.discordapi.context.deferrable.component.action.ButtonContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.Modal;
import dev.sbs.discordapi.response.component.layout.ActionRow;
import dev.sbs.discordapi.response.component.type.EventComponent;
import dev.sbs.discordapi.response.component.type.ToggleableComponent;
import dev.sbs.discordapi.response.component.type.v2.AccessoryComponent;
import dev.sbs.discordapi.response.handler.item.search.Search;
import dev.sbs.discordapi.response.handler.item.sorter.Sorter;
import dev.sbs.discordapi.response.page.Page;
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
public final class Button implements ActionComponent, AccessoryComponent, EventComponent<ButtonContext>, ToggleableComponent {

    private static final Function<ButtonContext, Mono<Void>> NOOP_HANDLER = ComponentContext::deferEdit;
    private final @NotNull String userIdentifier;
    private final @NotNull Style style;
    private final @NotNull Optional<Emoji> emoji;
    private final @NotNull Optional<String> label;
    private final @NotNull Optional<String> url;
    private final boolean deferEdit;
    private final @NotNull PageType pageType;
    private final @NotNull Function<ButtonContext, Mono<Void>> interaction;
    private boolean enabled;

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
            .append(this.getUserIdentifier(), button.getUserIdentifier())
            .append(this.getStyle(), button.getStyle())
            .append(this.isEnabled(), button.isEnabled())
            .append(this.getEmoji(), button.getEmoji())
            .append(this.getLabel(), button.getLabel())
            .append(this.getUrl(), button.getUrl())
            .append(this.isDeferEdit(), button.isDeferEdit())
            .append(this.getPageType(), button.getPageType())
            .build();
    }

    public static @NotNull Builder from(@NotNull Button button) {
        return new Builder()
            .withIdentifier(button.getUserIdentifier())
            .withStyle(button.getStyle())
            .setDisabled(button.isEnabled())
            .withEmoji(button.getEmoji())
            .withLabel(button.getLabel())
            .withUrl(button.getUrl())
            .withDeferEdit(button.isDeferEdit())
            .withPageType(button.getPageType())
            .onInteract(button.getInteraction());
    }

    @Override
    public @NotNull discord4j.core.object.component.Button getD4jComponent() {
        discord4j.core.object.emoji.Emoji d4jReaction = this.getEmoji().map(Emoji::getD4jReaction).orElse(null);
        String label = this.getLabel().orElse(null);

        return (switch (this.getStyle()) {
            case PRIMARY -> discord4j.core.object.component.Button.primary(this.getUserIdentifier(), d4jReaction, label);
            case SUCCESS -> discord4j.core.object.component.Button.success(this.getUserIdentifier(), d4jReaction, label);
            case DANGER -> discord4j.core.object.component.Button.danger(this.getUserIdentifier(), d4jReaction, label);
            case LINK -> discord4j.core.object.component.Button.link(this.getUrl().orElse(""), d4jReaction, label);
            case SECONDARY, UNKNOWN -> discord4j.core.object.component.Button.secondary(this.getUserIdentifier(), d4jReaction, label);
        }).disabled(this.isEnabled());
    }

    @Override
    public @NotNull Type getType() {
        return Type.BUTTON;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getUserIdentifier())
            .append(this.getStyle())
            .append(this.isEnabled())
            .append(this.getEmoji())
            .append(this.getLabel())
            .append(this.getUrl())
            .append(this.getPageType())
            .build();
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder implements ClassBuilder<Button> {

        @BuildFlag(nonNull = true)
        private String identifier;
        @BuildFlag(nonNull = true)
        private Style style = Style.UNKNOWN;
        private boolean disabled;
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
                this.emoji,
                this.label,
                this.url,
                this.deferEdit,
                this.pageType,
                this.interaction.orElse(NOOP_HANDLER),
                this.disabled
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

    // TODO: MOVE INTERACTION CODE TO BOT STARTUP ABSTRACTION
    @Getter
    @RequiredArgsConstructor
    public enum PageType {

        NONE("", __ -> Mono.empty()),
        PREVIOUS("Previous", context -> context.consumeResponse(response -> response.getHistoryHandler()
            .getCurrentPage()
            .getItemHandler()
            .gotoPreviousPage()
        )),
        SEARCH("Search", context -> context.withResponse(response -> context.presentModal(
            // TODO: Use labels and better TextInput.SearchType
            Modal.builder()
                .withComponents(
                    ActionRow.of(TextInput.SearchType.PAGE.build(response.getHistoryHandler().getCurrentPage().getItemHandler())),
                    ActionRow.of(TextInput.SearchType.INDEX.build(response.getHistoryHandler().getCurrentPage().getItemHandler()))
                )
                .withComponents(
                    response.getHistoryHandler()
                        .getCurrentPage()
                        .getItemHandler()
                        .getSearchHandler()
                        .getItems()
                        .stream()
                        .map(Search::getTextInput)
                        .map(ActionRow::of)
                        .collect(Concurrent.toList())
                )
                .withTitle("Search")
                .build()
        ))),
        INDEX("Index", __ -> Mono.empty()),
        FILTER("Filter", context -> context.withResponse(response -> context.followup(
            Response.builder()
                .withPages(
                    Page.builder()
                        .withComponents(
                            ActionRow.of(
                                SelectMenu.builder()
                                    .withOptions(
                                        SelectMenu.Option.builder()
                                            .withLabel("None")
                                            .build()
                                    )
                                    .withOptions(
                                        response.getHistoryHandler()
                                            .getCurrentPage()
                                            .getItemHandler()
                                            .getSortHandler()
                                            .getItems()
                                            .stream()
                                            .map(Sorter::getOption)
                                            .collect(Concurrent.toList())
                                    )
                                    .withPlaceholderUsesSelectedOption()
                                    .build(),
                                SelectMenu.builder()
                                    .build()
                            ),
                            ActionRow.of(
                                SelectMenu.builder()
                                    .withOptions(
                                        SelectMenu.Option.builder()
                                            .withLabel("None")
                                            .build()
                                    )
                                    .withOptions(
                                        // TODO: FilterHandler like SortHandler
                                        //       In the stream, build the select menu and
                                        //       then their action row and move it to it's own
                                        //       withComponents method
                                    )
                                    .withPlaceholderUsesSelectedOption()
                                    .build(),
                                SelectMenu.builder()
                                    .build()
                            )
                        )
                        .build()
                )
                .build()
        ))),
        NEXT("Next", context -> context.consumeResponse(response -> response.getHistoryHandler()
            .getCurrentPage()
            .getItemHandler()
            .gotoNextPage()
        ));
        //LAST("Last", 1, this.getEmoji("ARROW_SQUARE_LAST")),
        /*BACK("Back", this.getEmoji("ARROW_LEFT"), __ -> Mono.empty()),
        SORT("Sort", this.getEmoji("SORT"), context -> context.consumeResponse(response -> response.getHistoryHandler()
            .getCurrentPage()
            .getItemHandler()
            .getSortHandler()
            .gotoNext()
        )),
        ORDER("Order", this.getEmoji("SORT_DESCENDING"), context -> context.consumeResponse(response -> response.getHistoryHandler()
            .getCurrentPage()
            .getItemHandler()so
            .getSortHandler()
            .invertOrder()
        ))*/

        private final @NotNull String label;
        private final @NotNull Function<ButtonContext, Mono<Void>> interaction;

        public @NotNull Button build() {
            return this.build(Optional.empty());
        }

        public @NotNull Button build(@NotNull Optional<Emoji> emoji) {
            return Button.builder()
                .withStyle(Button.Style.SECONDARY)
                .withEmoji(emoji)
                .withLabel(this.getLabel())
                .withPageType(this)
                .setDisabled(true)
                .onInteract(this.getInteraction())
                .build();
        }

    }

}
