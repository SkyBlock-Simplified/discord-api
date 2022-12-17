package dev.sbs.discordapi.response.embed;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.ExceptionUtil;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.util.exception.DiscordException;
import discord4j.core.spec.EmbedCreateSpec;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Embed {

    @Getter private final UUID uniqueId;
    @LengthLimit(256)
    @Getter private final Optional<String> title;
    @LengthLimit(4096)
    @Getter private final Optional<String> description;
    @Getter private final Optional<String> url;
    @Getter private final Optional<Instant> timestamp;
    @Getter private final Optional<Color> color;
    @Getter private final Optional<String> imageUrl;
    @Getter private final Optional<String> thumbnailUrl;
    @Getter private final Optional<Footer> footer;
    @Getter private final Optional<Author> author;
    @Getter private final ConcurrentList<Field> fields;

    public static EmbedBuilder builder() {
        return new EmbedBuilder(UUID.randomUUID());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Embed embed = (Embed) o;

        return new EqualsBuilder()
            .append(this.getTitle(), embed.getTitle())
            .append(this.getDescription(), embed.getDescription())
            .append(this.getUrl(), embed.getUrl())
            .append(this.getTimestamp(), embed.getTimestamp())
            .append(this.getColor(), embed.getColor())
            .append(this.getImageUrl(), embed.getImageUrl())
            .append(this.getThumbnailUrl(), embed.getThumbnailUrl())
            .append(this.getFooter(), embed.getFooter())
            .append(this.getAuthor(), embed.getAuthor())
            .append(this.getFields(), embed.getFields())
            .build();
    }

    public static EmbedBuilder from(Embed embed) {
        return new EmbedBuilder(embed.getUniqueId())
            .withTitle(embed.getTitle())
            .withDescription(embed.getDescription())
            .withUrl(embed.getUrl())
            .withTimestamp(embed.getTimestamp())
            .withColor(embed.getColor())
            .withImageUrl(embed.getImageUrl())
            .withFooter(embed.getFooter())
            .withAuthor(embed.getAuthor())
            .withFields(embed.getFields());
    }

    public static EmbedBuilder from(@NotNull Throwable throwable) {
        return new EmbedBuilder(UUID.randomUUID())
            .withColor(Color.RED)
            .withTitle("An exception has occurred!")
            .withDescription(ExceptionUtil.getRootCauseMessage(throwable))
            .withTimestamp(Instant.now());
    }

    public EmbedCreateSpec getD4jEmbed() {
        EmbedCreateSpec.Builder builder = EmbedCreateSpec.builder()
            .title(this.getTitle().orElse(""))
            .description(this.getDescription().orElse(""))
            .url(this.getUrl().orElse(""))
            .color(discord4j.rest.util.Color.of(this.getColor().orElse(Color.WHITE).getRGB()))
            .image(this.getImageUrl().orElse(""))
            .thumbnail(this.getThumbnailUrl().orElse(""))
            .title(this.getTitle().orElse(""));

        this.getTimestamp().ifPresent(builder::timestamp);
        this.getFooter().ifPresent(footer -> builder.footer(footer.getD4jFooter()));
        this.getAuthor().ifPresent(author -> builder.author(author.getD4jAuthor()));
        this.getFields().forEach(field -> builder.addField(field.getD4jField()));
        return builder.build();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getUniqueId())
            .append(this.getTitle())
            .append(this.getDescription())
            .append(this.getUrl())
            .append(this.getTimestamp())
            .append(this.getColor())
            .append(this.getImageUrl())
            .append(this.getThumbnailUrl())
            .append(this.getFooter())
            .append(this.getAuthor())
            .append(this.getFields())
            .build();
    }

    public EmbedBuilder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class EmbedBuilder implements Builder<Embed> {

        private final UUID uniqueId;
        private Optional<String> title = Optional.empty();
        private Optional<String> description = Optional.empty();
        private Optional<String> url = Optional.empty();
        private Optional<Instant> timestamp = Optional.empty();
        private Optional<Color> color = Optional.empty();
        private Optional<String> imageUrl = Optional.empty();
        private Optional<String> thumbnailUrl = Optional.empty();
        private Optional<Footer> footer = Optional.empty();
        private Optional<Author> author = Optional.empty();
        private final ConcurrentList<Field> fields = Concurrent.newList();

        /**
         * Clears all existing {@link Field Fields}.
         */
        public EmbedBuilder clearFields() {
            this.fields.clear();
            return this;
        }

        /**
         * Sets the {@link Author} of the {@link Embed}.
         *
         * @param name The name of the author.
         */
        public EmbedBuilder withAuthor(@NotNull String name) {
            return this.withAuthor(name, Optional.empty());
        }

        /**
         * Sets the {@link Author} of the {@link Embed}.
         *
         * @param name The name of the author.
         * @param iconUrl The image icon of the author.
         */
        public EmbedBuilder withAuthor(@NotNull String name, @Nullable String iconUrl) {
            return this.withAuthor(name, Optional.ofNullable(iconUrl));
        }

        /**
         * Sets the {@link Author} of the {@link Embed}.
         *
         * @param name The name of the author.
         * @param iconUrl The image icon of the author.
         */
        public EmbedBuilder withAuthor(@NotNull String name, @NotNull Optional<String> iconUrl) {
            return this.withAuthor(name, iconUrl, Optional.empty());
        }

        /**
         * Sets the {@link Author} of the {@link Embed}.
         *
         * @param name The name of the author.
         * @param iconUrl The image icon of the author.
         * @param url The url of the author.
         */
        public EmbedBuilder withAuthor(@NotNull String name, @Nullable String iconUrl, @Nullable String url) {
            return this.withAuthor(name, Optional.ofNullable(iconUrl), url);
        }

        /**
         * Sets the {@link Author} of the {@link Embed}.
         *
         * @param name The name of the author.
         * @param iconUrl The image icon of the author.
         * @param url The url of the author.
         */
        public EmbedBuilder withAuthor(@NotNull String name, @Nullable String iconUrl, @NotNull Optional<String> url) {
            return this.withAuthor(name, Optional.ofNullable(iconUrl), url);
        }

        /**
         * Sets the {@link Author} of the {@link Embed}.
         *
         * @param name The name of the author.
         * @param iconUrl The image icon of the author.
         * @param url The url of the author.
         */
        public EmbedBuilder withAuthor(@NotNull String name, @NotNull Optional<String> iconUrl, @Nullable String url) {
            return this.withAuthor(name, iconUrl, Optional.ofNullable(url));
        }

        /**
         * Sets the {@link Author} of the {@link Embed}.
         *
         * @param name The name of the author.
         * @param iconUrl The image icon of the author.
         * @param url The url of the author.
         */
        public EmbedBuilder withAuthor(@NotNull String name, @NotNull Optional<String> iconUrl, @NotNull Optional<String> url) {
            return this.withAuthor(Author.of(name, iconUrl, url));
        }

        /**
         * Sets the {@link Author} of the {@link Embed}.
         *
         * @param author The author of the embed.
         */
        public EmbedBuilder withAuthor(@Nullable Author author) {
            return this.withAuthor(Optional.ofNullable(author));
        }

        /**
         * Sets the {@link Author} of the {@link Embed}.
         *
         * @param author The author of the embed.
         */
        public EmbedBuilder withAuthor(Optional<Author> author) {
            this.author = author;
            return this;
        }

        /**
         * Sets the line color used on the left side of the {@link Embed}.
         *
         * @param color The color of the embed.
         */
        public EmbedBuilder withColor(@Nullable Color color) {
            return this.withColor(Optional.ofNullable(color));
        }

        /**
         * Sets the line color used on the left side of the {@link Embed}.
         *
         * @param color The color of the embed.
         */
        public EmbedBuilder withColor(Optional<Color> color) {
            this.color = color;
            return this;
        }

        /**
         * Formats the description of the {@link Embed} with the given objects.
         *
         * @param description The description of the embed.
         * @param objects Objects used to format the description.
         */
        public EmbedBuilder withDescription(@NotNull String description, @NotNull Object... objects) {
            return this.withDescription(FormatUtil.format(description, objects));
        }

        /**
         * Sets the description of the {@link Embed}.
         *
         * @param description The description of the embed.
         */
        public EmbedBuilder withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        /**
         * Sets the description of the {@link Embed}.
         *
         * @param description The description of the embed.
         */
        public EmbedBuilder withDescription(@NotNull Optional<String> description) {
            description.ifPresent(value -> validateLength(Embed.class, "description", value));
            this.description = description;
            return this;
        }

        /**
         * Adds an empty {@link Field} to the {@link Embed}.
         */
        public EmbedBuilder withEmptyField() {
            return this.withEmptyField(false);
        }

        /**
         * Adds an empty {@link Field} to the {@link Embed}.
         *
         * @param inline True if field should render inline.
         */
        public EmbedBuilder withEmptyField(boolean inline) {
            return this.withField(null, null, inline);
        }

        /**
         * Adds a {@link Field} to the {@link Embed}.
         *
         * @param name The name of the field.
         * @param value The value of the field.
         */
        public EmbedBuilder withField(@Nullable String name, @Nullable String value) {
            return this.withField(name, value, false);
        }

        /**
         * Adds a {@link Field} to the {@link Embed}.
         *
         * @param name The name of the field.
         * @param value The value of the field.
         * @param inline True if field should render inline.
         */
        public EmbedBuilder withField(@Nullable String name, @Nullable String value, boolean inline) {
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
        public EmbedBuilder withFields(@NotNull Field... fields) {
            return this.withFields(Arrays.asList(fields));
        }

        /**
         * Adds the fields of a {@link SimplifiedException} to the fields of the {@link Embed}.
         *
         * @param simplifiedException The exception with fields to add.
         */
        public EmbedBuilder withFields(@NotNull SimplifiedException simplifiedException) {
            return this.withFields(
                simplifiedException.getFields()
                    .stream()
                    .map(Field::of)
                    .collect(Concurrent.toList())
            );
        }

        /**
         * Adds {@link Field Fields} to the {@link Embed}.
         *
         * @param fields Collection of fields to add.
         */
        public EmbedBuilder withFields(@NotNull Iterable<Field> fields) {
            if (this.fields.size() == Field.MAX_ALLOWED)
                throw SimplifiedException.of(DiscordException.class)
                    .withMessage("Number of fields cannot exceed {0}!", Field.MAX_ALLOWED)
                    .build();

            List<Field> fieldList = List.class.isAssignableFrom(fields.getClass()) ? (List<Field>) fields : StreamSupport.stream(fields.spliterator(), false).toList();
            IntStream.range(0, Math.min(fieldList.size(), (Field.MAX_ALLOWED - this.fields.size()))).forEach(index -> this.fields.add(fieldList.get(index)));
            return this;
        }

        /**
         * Sets the {@link Footer} of the {@link Embed}.
         *
         * @param text The text of the footer.
         */
        public EmbedBuilder withFooter(@NotNull String text) {
            return this.withFooter(text, Optional.empty());
        }

        /**
         * Sets the {@link Footer} of the {@link Embed}.
         *
         * @param text The text of the footer.
         * @param iconUrl The image icon of the footer.
         */
        public EmbedBuilder withFooter(@NotNull String text, @Nullable String iconUrl) {
            return this.withFooter(text, Optional.ofNullable(iconUrl));
        }

        /**
         * Sets the {@link Footer} of the {@link Embed}.
         *
         * @param text The text of the footer.
         * @param iconUrl The image icon of the footer.
         */
        public EmbedBuilder withFooter(@NotNull String text, @NotNull Optional<String> iconUrl) {
            return this.withFooter(Footer.of(text, iconUrl));
        }

        /**
         * Sets the {@link Footer} of the {@link Embed}.
         *
         * @param footer The footer of the embed.
         */
        public EmbedBuilder withFooter(@Nullable Footer footer) {
            return this.withFooter(Optional.ofNullable(footer));
        }

        /**
         * Sets the {@link Footer} of the {@link Embed}.
         *
         * @param footer The footer of the embed.
         */
        public EmbedBuilder withFooter(@NotNull Optional<Footer> footer) {
            this.footer = footer;
            return this;
        }

        /**
         * Formats the image url used in the {@link Embed} with the given objects.
         *
         * @param imageUrl The url of the embed image.
         * @param objects Objects used to format the image url.
         */
        public EmbedBuilder withImageUrl(@NotNull String imageUrl, @NotNull Object... objects) {
            return this.withImageUrl(FormatUtil.format(imageUrl, objects));
        }

        /**
         * Sets the image url used in the {@link Embed}.
         *
         * @param imageUrl The url of the embed image.
         */
        public EmbedBuilder withImageUrl(@Nullable String imageUrl) {
            return this.withImageUrl(Optional.ofNullable(imageUrl));
        }

        /**
         * Sets the image url used in the {@link Embed}.
         *
         * @param imageUrl The url of the embed image.
         */
        public EmbedBuilder withImageUrl(Optional<String> imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        /**
         * Formats the thumbnail image url used in the {@link Embed} with the given objects.
         *
         * @param thumbnailUrl The url of the embed thumbnail image.
         * @param objects Objects used to format the thumbnail image url.
         */
        public EmbedBuilder withThumbnailUrl(@NotNull String thumbnailUrl, @NotNull Object... objects) {
            return this.withThumbnailUrl(FormatUtil.format(thumbnailUrl, objects));
        }

        /**
         * Sets the thumbnail image url used in the {@link Embed}.
         *
         * @param thumbnailUrl The url of the embed thumbnail image.
         */
        public EmbedBuilder withThumbnailUrl(@Nullable String thumbnailUrl) {
            return this.withThumbnailUrl(Optional.ofNullable(thumbnailUrl));
        }

        /**
         * Sets the thumbnail image url used in the {@link Embed}.
         *
         * @param thumbnailUrl The url of the embed thumbnail image.
         */
        public EmbedBuilder withThumbnailUrl(Optional<String> thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
            return this;
        }

        /**
         * Formats the title text of the {@link Embed} with given objects.
         *
         * @param title Title of the embed.
         * @param objects Objects used to format the title.
         */
        public EmbedBuilder withTitle(@NotNull String title, @NotNull Object... objects) {
            return this.withTitle(FormatUtil.format(title, objects));
        }

        /**
         * Sets the title text of the {@link Embed}.
         *
         * @param title Title of the embed.
         */
        public EmbedBuilder withTitle(@Nullable String title) {
            return this.withTitle(Optional.ofNullable(title));
        }

        /**
         * Sets the title text of the {@link Embed}.
         *
         * @param title Title of the embed.
         */
        public EmbedBuilder withTitle(Optional<String> title) {
            title.ifPresent(value -> validateLength(Embed.class, "title", value));
            this.title = title;
            return this;
        }

        /**
         * Sets the timestamp to be shown in the {@link Footer} of the {@link Embed}.
         *
         * @param timestamp Timestamp of the embed.
         */
        public EmbedBuilder withTimestamp(@Nullable Instant timestamp) {
            return this.withTimestamp(Optional.ofNullable(timestamp));
        }

        /**
         * Sets the timestamp to be shown in the {@link Footer} of the {@link Embed}.
         *
         * @param timestamp Timestamp of the embed.
         */
        public EmbedBuilder withTimestamp(Optional<Instant> timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Sets the url used in the {@link Embed}'s title.
         * <br><br>
         * See {@link #getTitle()}.
         *
         * @param url The url of the embed title.
         */
        public EmbedBuilder withUrl(@Nullable String url) {
            return this.withUrl(Optional.ofNullable(url));
        }

        /**
         * Sets the url of the {@link Embed}'s title.
         * <br><br>
         * See {@link #getTitle()}.
         *
         * @param url The url of the embed title.
         */
        public EmbedBuilder withUrl(Optional<String> url) {
            this.url = url;
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link SelectMenu} component.
         */
        @Override
        public Embed build() {
            return new Embed(
                this.uniqueId,
                this.title,
                this.description,
                this.url,
                this.timestamp,
                this.color,
                this.imageUrl,
                this.thumbnailUrl,
                this.footer,
                this.author,
                Concurrent.newUnmodifiableList(this.fields)
            );
        }

        static void validateLength(Class<?> tClass, String fieldName, String value) {
            if (StringUtil.isNotEmpty(value)) {
                Reflection.of(tClass)
                    .getField(fieldName)
                    .getAnnotation(LengthLimit.class)
                    .ifPresent(lengthLimit -> {
                        if (StringUtil.trim(value).length() > lengthLimit.value())
                            throw SimplifiedException.of(DiscordException.class)
                                .withMessage("The maximum allowed length of {0} ''{1}'' is {2} characters.", tClass.getSimpleName(), fieldName, lengthLimit.value())
                                .build();
                    });
            }
        }

    }

}