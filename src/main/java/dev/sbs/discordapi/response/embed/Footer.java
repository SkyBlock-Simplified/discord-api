package dev.sbs.discordapi.response.embed;

import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import discord4j.core.spec.EmbedCreateFields;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Footer {

    @LengthLimit(2048)
    @Getter private final String text;
    @Getter private final Optional<String> iconUrl;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Footer footer = (Footer) o;

        return new EqualsBuilder()
            .append(this.getText(), footer.getText())
            .append(this.getIconUrl(), footer.getIconUrl())
            .build();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getText())
            .append(this.getIconUrl())
            .build();
    }

    public EmbedCreateFields.Footer getD4jFooter() {
        return EmbedCreateFields.Footer.of(this.getText(), this.getIconUrl().orElse(null));
    }

    public static Footer of(@NotNull String text) {
        return of(text, Optional.empty());
    }

    public static Footer of(@NotNull String text, @Nullable String iconUrl) {
        return of(text, Optional.ofNullable(iconUrl));
    }

    public static Footer of(@NotNull String text, @NotNull Optional<String> iconUrl) {
        Embed.EmbedBuilder.validateLength(Footer.class, "text", text);
        return new Footer(text, iconUrl);
    }

}