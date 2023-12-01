package dev.sbs.discordapi.response.embed.structure;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.builder.annotation.BuildFlag;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.api.util.helper.StringUtil;
import discord4j.core.spec.EmbedCreateFields;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public class Footer {

    private final @NotNull String text;
    private final @NotNull Optional<String> iconUrl;
    private final @NotNull Optional<Instant> timestamp;

    public static @NotNull Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Footer footer = (Footer) o;

        return new EqualsBuilder()
            .append(this.getText(), footer.getText())
            .append(this.getIconUrl(), footer.getIconUrl())
            .append(this.getTimestamp(), footer.getTimestamp())
            .build();
    }

    public static @NotNull Builder from(@NotNull Footer footer) {
        return builder()
            .withText(footer.getText())
            .withIconUrl(footer.getIconUrl())
            .withTimestamp(footer.getTimestamp());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getText())
            .append(this.getIconUrl())
            .append(this.getTimestamp())
            .build();
    }

    public @NotNull EmbedCreateFields.Footer getD4jFooter() {
        return EmbedCreateFields.Footer.of(this.getText(), this.getIconUrl().orElse(null));
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements dev.sbs.api.util.builder.Builder<Footer> {

        @BuildFlag(required = true, limit = 2046)
        private String text;
        private Optional<String> iconUrl = Optional.empty();
        private Optional<Instant> timestamp = Optional.empty();

        /**
         * Sets the icon url of the {@link Footer}.
         *
         * @param iconUrl The icon url of the footer.
         */
        public Builder withIconUrl(@Nullable String iconUrl) {
            return this.withIconUrl(Optional.ofNullable(iconUrl));
        }

        /**
         * Sets the icon url of the {@link Footer}.
         *
         * @param iconUrl The icon url of the footer.
         * @param args The objects to format with.
         */
        public Builder withIconUrl(@PrintFormat @Nullable String iconUrl, @NotNull Object... args) {
            return this.withIconUrl(StringUtil.formatNullable(iconUrl, args));
        }

        /**
         * Sets the icon url of the {@link Footer}.
         *
         * @param iconUrl The icon url of the footer.
         */
        public Builder withIconUrl(@NotNull Optional<String> iconUrl) {
            this.iconUrl = iconUrl;
            return this;
        }

        /**
         * Sets the text of the {@link Footer}.
         *
         * @param text The text of the footer.
         */
        public Builder withText(@NotNull String text) {
            this.text = text;
            return this;
        }

        /**
         * Sets the text of the {@link Footer}.
         *
         * @param text The text of the footer.
         * @param args The objects to format with.
         */
        public Builder withText(@PrintFormat @NotNull String text, @NotNull Object... args) {
            this.text = String.format(text, args);
            return this;
        }

        /**
         * Sets the timestamp to be shown in the {@link Footer}.
         *
         * @param timestamp Timestamp of the embed.
         */
        public Builder withTimestamp(@Nullable Instant timestamp) {
            return this.withTimestamp(Optional.ofNullable(timestamp));
        }

        /**
         * Sets the timestamp to be shown in the {@link Footer}.
         *
         * @param timestamp Timestamp of the embed.
         */
        public Builder withTimestamp(@NotNull Optional<Instant> timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        @Override
        public @NotNull Footer build() {
            Reflection.validateFlags(this);

            return new Footer(
                this.text,
                this.iconUrl,
                this.timestamp
            );
        }

    }

}