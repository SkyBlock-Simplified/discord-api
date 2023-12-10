package dev.sbs.discordapi.response.embed;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.builder.annotation.BuildFlag;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.ExceptionUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.component.type.IdentifiableComponent;
import dev.sbs.discordapi.response.embed.structure.Author;
import dev.sbs.discordapi.response.embed.structure.Field;
import dev.sbs.discordapi.response.embed.structure.Footer;
import dev.sbs.discordapi.response.page.item.AuthorItem;
import dev.sbs.discordapi.response.page.item.DescriptionItem;
import dev.sbs.discordapi.response.page.item.FooterItem;
import dev.sbs.discordapi.response.page.item.ImageUrlItem;
import dev.sbs.discordapi.response.page.item.ThumbnailUrlItem;
import dev.sbs.discordapi.response.page.item.TitleItem;
import dev.sbs.discordapi.response.page.item.type.Item;
import dev.sbs.discordapi.response.page.item.type.RenderItem;
import dev.sbs.discordapi.util.exception.DiscordException;
import discord4j.core.spec.EmbedCreateSpec;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

@Getter
@AllArgsConstructor
public class Embed implements IdentifiableComponent {

    private final @NotNull String identifier;
    private final @NotNull Optional<Color> color;
    private final @NotNull Optional<Author> author;
    private final @NotNull Optional<String> title;
    private final @NotNull Optional<String> url;
    private final @NotNull Optional<String> thumbnailUrl;
    private final @NotNull Optional<String> description;
    private final @NotNull Optional<String> imageUrl;
    private final @NotNull Optional<Footer> footer;
    private final @NotNull ConcurrentList<Field> fields;

    public static @NotNull Embed.Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Embed embed = (Embed) o;

        return new EqualsBuilder()
            .append(this.getIdentifier(), embed.getIdentifier())
            .append(this.getColor(), embed.getColor())
            .append(this.getAuthor(), embed.getAuthor())
            .append(this.getTitle(), embed.getTitle())
            .append(this.getUrl(), embed.getUrl())
            .append(this.getThumbnailUrl(), embed.getThumbnailUrl())
            .append(this.getDescription(), embed.getDescription())
            .append(this.getImageUrl(), embed.getImageUrl())
            .append(this.getFooter(), embed.getFooter())
            .append(this.getFields(), embed.getFields())
            .build();
    }

    public static @NotNull Builder from(@NotNull Embed embed) {
        return new Builder()
            .withIdentifier(embed.getIdentifier())
            .withColor(embed.getColor())
            .withAuthor(embed.getAuthor())
            .withTitle(embed.getTitle())
            .withUrl(embed.getUrl())
            .withThumbnailUrl(embed.getThumbnailUrl())
            .withDescription(embed.getDescription())
            .withImageUrl(embed.getImageUrl())
            .withFooter(embed.getFooter())
            .withFields(embed.getFields());
    }

    public static @NotNull Builder from(@NotNull Throwable throwable) {
        return new Builder()
            .withIdentifier(UUID.randomUUID().toString())
            .withColor(Color.RED)
            .withTitle("An exception has occurred!")
            .withDescription(ExceptionUtil.getRootCauseMessage(throwable))
            .withFooter(
                Footer.builder()
                    .withTimestamp(Instant.now())
                    .build()
            );
    }

    public @NotNull EmbedCreateSpec getD4jEmbed() {
        EmbedCreateSpec.Builder builder = EmbedCreateSpec.builder()
            .title(this.getTitle().orElse(""))
            .description(this.getDescription().orElse(""))
            .url(this.getUrl().orElse(""))
            .color(discord4j.rest.util.Color.of(this.getColor().orElse(Color.WHITE).getRGB()))
            .image(this.getImageUrl().orElse(""))
            .thumbnail(this.getThumbnailUrl().orElse(""))
            .title(this.getTitle().orElse(""))
            .fields(this.getFields().stream().map(Field::getD4jField).collect(Concurrent.toList()));

        this.getAuthor().ifPresent(author -> builder.author(author.getD4jAuthor()));

        this.getFooter().ifPresent(footer -> {
            builder.footer(footer.getD4jFooter());
            footer.getTimestamp().ifPresent(builder::timestamp);
        });

        return builder.build();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getIdentifier())
            .append(this.getColor())
            .append(this.getAuthor())
            .append(this.getTitle())
            .append(this.getUrl())
            .append(this.getThumbnailUrl())
            .append(this.getDescription())
            .append(this.getImageUrl())
            .append(this.getFooter())
            .append(this.getFields())
            .build();
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements dev.sbs.api.util.builder.Builder<Embed> {

        @BuildFlag(required = true)
        private String identifier;
        @BuildFlag(limit = 256)
        private Optional<String> title = Optional.empty();
        @BuildFlag(limit = 4096)
        private Optional<String> description = Optional.empty();
        private Optional<String> url = Optional.empty();
        private Optional<Color> color = Optional.empty();
        private Optional<String> imageUrl = Optional.empty();
        private Optional<String> thumbnailUrl = Optional.empty();
        private Optional<Footer> footer = Optional.empty();
        private Optional<Author> author = Optional.empty();
        @BuildFlag(limit = Field.MAX_ALLOWED)
        private ConcurrentList<Field> fields = Concurrent.newList();

        /**
         * Clears all existing {@link Field Fields}.
         */
        public Builder clearFields() {
            this.fields.clear();
            return this;
        }

        /**
         * Sets the {@link Author} of the {@link Embed}.
         *
         * @param author The author of the embed.
         */
        public Builder withAuthor(@Nullable Author author) {
            return this.withAuthor(Optional.ofNullable(author));
        }

        /**
         * Sets the {@link Author} of the {@link Embed}.
         *
         * @param author The author of the embed.
         */
        public Builder withAuthor(@NotNull Optional<Author> author) {
            this.author = author;
            return this;
        }

        /**
         * Sets the line color used on the left side of the {@link Embed}.
         *
         * @param color The color of the embed.
         */
        public Builder withColor(@Nullable Color color) {
            return this.withColor(Optional.ofNullable(color));
        }

        /**
         * Sets the line color used on the left side of the {@link Embed}.
         *
         * @param color The color of the embed.
         */
        public Builder withColor(@NotNull Optional<Color> color) {
            this.color = color;
            return this;
        }

        /**
         * Formats the description of the {@link Embed} with the given objects.
         *
         * @param description The description of the embed.
         */
        public Builder withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        /**
         * Formats the description of the {@link Embed} with the given objects.
         *
         * @param description The description of the embed.
         */
        public Builder withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        /**
         * Sets the description of the {@link Embed}.
         *
         * @param description The description of the embed.
         */
        public Builder withDescription(@NotNull Optional<String> description) {
            this.description = description;
            return this;
        }

        /**
         * Adds an empty {@link Field} to the {@link Embed}.
         */
        public Builder withEmptyField() {
            return this.withEmptyField(false);
        }

        /**
         * Adds an empty {@link Field} to the {@link Embed}.
         *
         * @param inline True if field should render inline.
         */
        public Builder withEmptyField(boolean inline) {
            return this.withField(null, null, inline);
        }

        /**
         * Adds a {@link Field} to the {@link Embed}.
         *
         * @param name The name of the field.
         * @param value The value of the field.
         */
        public Builder withField(@Nullable String name, @Nullable String value) {
            return this.withField(name, value, false);
        }

        /**
         * Adds a {@link Field} to the {@link Embed}.
         *
         * @param name The name of the field.
         * @param value The value of the field.
         * @param inline True if field should render inline.
         */
        public Builder withField(@Nullable String name, @Nullable String value, boolean inline) {
            return this.withFields(
                Field.builder()
                    .withName(name)
                    .withValue(value)
                    .isInline(inline)
                    .build()
            );
        }

        /**
         * Adds {@link Field Fields} to the {@link Embed}.
         *
         * @param fields Variable number of fields to add.
         */
        public Builder withFields(@NotNull Field... fields) {
            return this.withFields(Arrays.asList(fields));
        }

        /**
         * Adds {@link Field Fields} to the {@link Embed}.
         *
         * @param fields Collection of fields to add.
         */
        public Builder withFields(@NotNull Iterable<Field> fields) {
            if (this.fields.size() == Field.MAX_ALLOWED)
                throw SimplifiedException.of(DiscordException.class)
                    .withMessage("Number of fields cannot exceed %s.", Field.MAX_ALLOWED)
                    .build();

            List<Field> fieldList = List.class.isAssignableFrom(fields.getClass()) ? (List<Field>) fields : StreamSupport.stream(fields.spliterator(), false).toList();
            IntStream.range(0, Math.min(fieldList.size(), (Field.MAX_ALLOWED - this.fields.size()))).forEach(index -> this.fields.add(fieldList.get(index)));
            return this;
        }

        /**
         * Adds the fields of a {@link SimplifiedException} to the fields of the {@link Embed}.
         *
         * @param simplifiedException The exception with fields to add.
         */
        public Builder withFields(@NotNull SimplifiedException simplifiedException) {
            return this.withFields(
                simplifiedException.getFields()
                    .stream()
                    .map(Field::from)
                    .map(Field.Builder::build)
                    .collect(Concurrent.toList())
            );
        }

        /**
         * Overrides the default identifier of the {@link Embed}.
         *
         * @param identifier The identifier to use.
         */
        public Builder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Overrides the default identifier of the {@link Embed}.
         *
         * @param identifier The identifier to use.
         * @param args The objects used to format the identifier.
         */
        public Builder withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
            this.identifier = String.format(identifier, args);
            return this;
        }

        /**
         * Add {@link Item Items} to the {@link Embed}.
         *
         * @param items Variable number of items to add.
         */
        public Builder withItems(@NotNull Item... items) {
            return this.withItems(Arrays.asList(items));
        }

        /**
         * Add {@link Item Items} to the {@link Embed}.
         *
         * @param items Collection of non-page items to add.
         */
        public Builder withItems(@NotNull Iterable<Item> items) {
            items.forEach(item -> {
                switch (item.getType()) {
                    case AUTHOR -> this.withAuthor(item.asType(AuthorItem.class).asAuthor());
                    case DESCRIPTION -> this.withDescription(item.asType(DescriptionItem.class).getValue());
                    case FOOTER -> this.withFooter(item.asType(FooterItem.class).asFooter());
                    case IMAGE_URL -> this.withImageUrl(item.asType(ImageUrlItem.class).getValue());
                    case FIELD -> this.withFields(item.asType(RenderItem.class).getRenderField());
                    case THUMBNAIL_URL -> this.withThumbnailUrl(item.asType(ThumbnailUrlItem.class).getValue());
                    case TITLE -> {
                        TitleItem titleItem = item.asType(TitleItem.class);
                        this.withTitle(titleItem.getText());
                        this.withUrl(titleItem.getUrl());
                    }
                }
            });

            return this;
        }

        /**
         * Sets the {@link Footer} of the {@link Embed}.
         *
         * @param footer The footer of the embed.
         */
        public Builder withFooter(@Nullable Footer footer) {
            return this.withFooter(Optional.ofNullable(footer));
        }

        /**
         * Sets the {@link Footer} of the {@link Embed}.
         *
         * @param footer The footer of the embed.
         */
        public Builder withFooter(@NotNull Optional<Footer> footer) {
            this.footer = footer;
            return this;
        }

        /**
         * Sets the image url used in the {@link Embed}.
         *
         * @param imageUrl The url of the embed image.
         */
        public Builder withImageUrl(@Nullable String imageUrl) {
            return this.withImageUrl(Optional.ofNullable(imageUrl));
        }

        /**
         * Formats the image url used in the {@link Embed} with the given objects.
         *
         * @param imageUrl The url of the embed image.
         * @param args Objects used to format the image url.
         */
        public Builder withImageUrl(@PrintFormat @Nullable String imageUrl, @Nullable Object... args) {
            return this.withImageUrl(StringUtil.formatNullable(imageUrl, args));
        }

        /**
         * Sets the image url used in the {@link Embed}.
         *
         * @param imageUrl The url of the embed image.
         */
        public Builder withImageUrl(@NotNull Optional<String> imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        /**
         * Sets the thumbnail image url used in the {@link Embed}.
         *
         * @param thumbnailUrl The url of the embed thumbnail image.
         */
        public Builder withThumbnailUrl(@Nullable String thumbnailUrl) {
            return this.withThumbnailUrl(Optional.ofNullable(thumbnailUrl));
        }

        /**
         * Formats the thumbnail image url used in the {@link Embed} with the given objects.
         *
         * @param thumbnailUrl The url of the embed thumbnail image.
         * @param args Objects used to format the thumbnail image url.
         */
        public Builder withThumbnailUrl(@PrintFormat @Nullable String thumbnailUrl, @Nullable Object... args) {
            return this.withThumbnailUrl(StringUtil.formatNullable(thumbnailUrl, args));
        }

        /**
         * Sets the thumbnail image url used in the {@link Embed}.
         *
         * @param thumbnailUrl The url of the embed thumbnail image.
         */
        public Builder withThumbnailUrl(@NotNull Optional<String> thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
            return this;
        }

        /**
         * Sets the title text of the {@link Embed}.
         *
         * @param title Title of the embed.
         */
        public Builder withTitle(@Nullable String title) {
            return this.withTitle(Optional.ofNullable(title));
        }

        /**
         * Formats the title text of the {@link Embed} with given objects.
         *
         * @param title Title of the embed.
         * @param args Objects used to format the title.
         */
        public Builder withTitle(@PrintFormat @Nullable String title, @Nullable Object... args) {
            return this.withTitle(StringUtil.formatNullable(title, args));
        }

        /**
         * Sets the title text of the {@link Embed}.
         *
         * @param title Title of the embed.
         */
        public Builder withTitle(@NotNull Optional<String> title) {
            this.title = title;
            return this;
        }

        /**
         * Sets the url used in the {@link Embed}'s title.
         * <br><br>
         * See {@link #getTitle()}.
         *
         * @param url The url of the embed title.
         */
        public Builder withUrl(@Nullable String url) {
            return this.withUrl(Optional.ofNullable(url));
        }

        /**
         * Sets the url used in the {@link Embed}'s title.
         * <br><br>
         * See {@link #getTitle()}.
         *
         * @param url The url of the embed title.
         * @param args Objects used to format the url.
         */
        public Builder withUrl(@PrintFormat @Nullable String url, @Nullable Object... args) {
            return this.withUrl(StringUtil.formatNullable(url, args));
        }

        /**
         * Sets the url of the {@link Embed}'s title.
         * <br><br>
         * See {@link #getTitle()}.
         *
         * @param url The url of the embed title.
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
        public @NotNull Embed build() {
            Reflection.validateFlags(this);

            return new Embed(
                this.identifier,
                this.color,
                this.author,
                this.title,
                this.url,
                this.thumbnailUrl,
                this.description,
                this.imageUrl,
                this.footer,
                this.fields.toUnmodifiableList()
            );
        }

    }

}