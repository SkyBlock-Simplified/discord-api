package dev.sbs.discordapi.response.embed;

import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import discord4j.core.spec.EmbedCreateFields;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Author {

    @LengthLimit(256)
    @Getter private final String name;
    @Getter private final Optional<String> iconUrl;
    @Getter private final Optional<String> url;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Author author = (Author) o;

        return new EqualsBuilder()
            .append(this.getName(), author.getName())
            .append(this.getUrl(), author.getUrl())
            .append(this.getIconUrl(), author.getIconUrl())
            .build();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getName())
            .append(this.getUrl())
            .append(this.getIconUrl())
            .build();
    }

    public EmbedCreateFields.Author getD4jAuthor() {
        return EmbedCreateFields.Author.of(this.getName(), this.getUrl().orElse(null), this.getIconUrl().orElse(null));
    }

    public static Author of(@NotNull String name) {
        return of(name, Optional.empty());
    }

    public static Author of(@NotNull String name, @Nullable String iconUrl) {
        return of(name, Optional.ofNullable(iconUrl));
    }

    public static Author of(@NotNull String name, @NotNull Optional<String> iconUrl) {
        return of(name, iconUrl, Optional.empty());
    }

    public static Author of(@NotNull String name, @NotNull Optional<String> iconUrl, @Nullable String url) {
        return new Author(name, iconUrl, Optional.ofNullable(url));
    }

    public static Author of(@NotNull String name, @NotNull Optional<String> iconUrl, @NotNull Optional<String> url) {
        Embed.EmbedBuilder.validateLength(Author.class, "name", name);
        return new Author(name, iconUrl, url);
    }

}