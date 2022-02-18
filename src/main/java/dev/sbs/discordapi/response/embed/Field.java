package dev.sbs.discordapi.response.embed;

import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.api.util.tuple.Triple;
import discord4j.core.spec.EmbedCreateFields;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Field {

    public static final int MAX_ALLOWED = 25;
    public static final String ZERO_WIDTH_SPACE = "\u200B";

    @LengthLimit(256)
    @Getter private final String name;
    @LengthLimit(1024)
    @Getter private final String value;
    @Getter private final boolean inline;

    public static Field empty() {
        return empty(false);
    }

    public static Field empty(boolean inline) {
        return of(Optional.empty(), Optional.empty(), inline);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Field field = (Field) o;

        return new EqualsBuilder()
            .append(this.isInline(), field.isInline())
            .append(this.getName(), field.getName())
            .append(this.getValue(), field.getValue())
            .build();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getName())
            .append(this.getValue())
            .append(this.isInline())
            .build();
    }

    public EmbedCreateFields.Field getD4jField() {
        return EmbedCreateFields.Field.of(this.getName(), this.getValue(), this.isInline());
    }

    public static Field of(@NotNull Map.Entry<String, String> entry) {
        return of(entry.getKey(), entry.getValue());
    }

    public static Field of(@NotNull Triple<String, String, Boolean> triple) {
        return of(triple.getLeft(), triple.getMiddle(), triple.getRight());
    }

    public static Field of(@Nullable String name, @Nullable String value) {
        return of(name, value, false);
    }

    public static Field of(@Nullable String name, @Nullable String value, boolean inline) {
        return of(Optional.ofNullable(name), Optional.ofNullable(value), inline);
    }

    public static Field of(Optional<String> name, Optional<String> value, boolean inline) {
        name.ifPresent(nameValue -> Embed.EmbedBuilder.validateLength(Field.class, "name", nameValue));
        value.ifPresent(valueValue -> Embed.EmbedBuilder.validateLength(Field.class, "value", valueValue));
        String fieldName = StringUtil.defaultIfEmpty(name.orElse("").trim(), ZERO_WIDTH_SPACE);
        String fieldValue = StringUtil.defaultIfEmpty(value.orElse("").trim(), ZERO_WIDTH_SPACE);
        return new Field(fieldName, fieldValue, inline);
    }

}