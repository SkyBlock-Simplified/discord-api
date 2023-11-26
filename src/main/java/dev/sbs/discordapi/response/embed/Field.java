package dev.sbs.discordapi.response.embed;

import dev.sbs.api.util.builder.Builder;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.api.util.data.tuple.Triple;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.response.Emoji;
import discord4j.core.spec.EmbedCreateFields;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Field {

    public static final int MAX_ALLOWED = 25;

    @Getter private final Optional<Emoji> emoji;
    @LengthLimit(256)
    @Getter private final Optional<String> name;
    @LengthLimit(1024)
    @Getter private final Optional<String> value;
    @Getter private final boolean inline;

    public static FieldBuilder builder() {
        return new FieldBuilder();
    }

    public static Field empty() {
        return empty(false);
    }

    public static Field empty(boolean inline) {
        return builder().isInline(inline).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Field field = (Field) o;

        return new EqualsBuilder()
            .append(this.isInline(), field.isInline())
            .append(this.getEmoji(), field.getEmoji())
            .append(this.getName(), field.getName())
            .append(this.getValue(), field.getValue())
            .build();
    }

    public EmbedCreateFields.Field getD4jField() {
        return EmbedCreateFields.Field.of(
            String.format(
                "%s%s",
                this.getEmoji().map(Emoji::asSpacedFormat).orElse(""),
                this.getName().orElse(" ")
            ),
            this.getValue().orElse(" "),
            this.isInline()
        );
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getEmoji())
            .append(this.getName())
            .append(this.getValue())
            .append(this.isInline())
            .build();
    }

    public FieldBuilder mutate() {
        return new FieldBuilder()
            .withEmoji(this.getEmoji())
            .withName(this.getName())
            .withValue(this.getValue())
            .isInline(this.isInline());
    }

    public static Field of(@NotNull Triple<String, String, Boolean> triple) {
        return builder()
            .withName(triple.getLeft())
            .withValue(triple.getMiddle())
            .isInline(triple.getRight())
            .build();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class FieldBuilder implements Builder<Field> {

        private Optional<Emoji> emoji = Optional.empty();
        private Optional<String> name = Optional.empty();
        private Optional<String> value = Optional.empty();
        private boolean inline = false;

        /**
         * Sets the {@link Field} to render inline.
         */
        public FieldBuilder isInline() {
            return this.isInline(true);
        }

        /**
         * Sets whether the {@link Field} should render inline.
         *
         * @param inline True to render field inline.
         */
        public FieldBuilder isInline(boolean inline) {
            this.inline = inline;
            return this;
        }

        /**
         * Sets the emoji of the {@link Field}.
         *
         * @param emoji The emoji of the field.
         */
        public FieldBuilder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the emoji of the {@link Field}.
         *
         * @param emoji The emoji of the field.
         */
        public FieldBuilder withEmoji(@NotNull Optional<Emoji> emoji) {
            this.emoji = emoji;
            return this;
        }

        /**
         * Formats the name of the {@link Field} with the given objects.
         *
         * @param name The name of the field.
         * @param objects Objects used to format the name.
         */
        public FieldBuilder withName(@NotNull String name, @NotNull Object... objects) {
            return this.withName(String.format(name, objects));
        }

        /**
         * Sets the name of the {@link Field}.
         *
         * @param name The name of the field.
         */
        public FieldBuilder withName(@Nullable String name) {
            return this.withName(Optional.ofNullable(name));
        }

        /**
         * Sets the name of the {@link Field}.
         *
         * @param name The name of the field.
         */
        public FieldBuilder withName(@NotNull Optional<String> name) {
            name.ifPresent(nameValue -> Embed.EmbedBuilder.validateLength(Field.class, "name", nameValue));
            this.name = Optional.ofNullable(StringUtil.defaultIfEmpty(name.orElse("").trim(), null));
            return this;
        }

        /**
         * Formats the value of the {@link Field} with the given objects.
         *
         * @param value The value of the field.
         * @param objects Objects used to format the value.
         */
        public FieldBuilder withValue(@NotNull String value, @NotNull Object... objects) {
            return this.withValue(String.format(value, objects));
        }

        /**
         * Sets the value of the {@link Field}.
         *
         * @param value The value of the field.
         */
        public FieldBuilder withValue(@Nullable String value) {
            return this.withValue(Optional.ofNullable(value));
        }

        /**
         * Sets the value of the {@link Field}.
         *
         * @param value The value of the field.
         */
        public FieldBuilder withValue(@NotNull Optional<String> value) {
            value.ifPresent(valueValue -> Embed.EmbedBuilder.validateLength(Field.class, "value", valueValue));
            this.value = Optional.ofNullable(StringUtil.defaultIfEmpty(value.orElse("").trim(), null));
            return this;
        }


        @Override
        public @NotNull Field build() {
            return new Field(
                this.emoji,
                this.name,
                this.value,
                this.inline
            );
        }

    }

}