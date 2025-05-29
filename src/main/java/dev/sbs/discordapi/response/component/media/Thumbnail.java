package dev.sbs.discordapi.response.component.media;

import dev.sbs.api.util.StringUtil;
import dev.sbs.api.builder.ClassBuilder;
import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.type.v2.AccessoryComponent;
import discord4j.core.object.component.MediaGalleryItem;
import discord4j.core.object.component.UnfurledMediaItem;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.Optional;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Thumbnail implements Component, AccessoryComponent {

    private final @NotNull MediaData mediaData;
    private final @NotNull Optional<String> description;

    public static @NotNull Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Thumbnail thumbnail = (Thumbnail) o;

        return new EqualsBuilder()
            .append(this.getMediaData(), thumbnail.getMediaData())
            .append(this.getDescription(), thumbnail.getDescription())
            .build();
    }

    public static @NotNull Builder from(@NotNull Thumbnail thumbnail) {
        return builder()
            .withMediaData(thumbnail.getMediaData())
            .withDescription(thumbnail.getDescription());
    }

    @Override
    public @NotNull discord4j.core.object.component.Thumbnail getD4jComponent() {
        return discord4j.core.object.component.Thumbnail.of(
            this.getMediaData().getComponentId(),
            UnfurledMediaItem.of(this.getMediaData().getUrl()),
            this.getDescription().orElse(""),
            this.getMediaData().isSpoiler()
        );
    }

    public @NotNull MediaGalleryItem getD4jGalleryItem() {
        return MediaGalleryItem.of(
            UnfurledMediaItem.of(this.getMediaData().getUrl()),
            this.getDescription().orElse(""),
            this.getMediaData().isSpoiler()
        );
    }

    @Override
    public @NotNull Type getType() {
        return Type.THUMBNAIL;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getMediaData())
            .append(this.getDescription())
            .build();
    }

    public boolean isPendingUpload() {
        return this.getMediaData().isPendingUpload();
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    public @NotNull Builder mutate(@NotNull discord4j.core.object.component.Thumbnail d4jThumbnail) {
        return from(this).withMediaData(this.getMediaData().mutate(d4jThumbnail.getMedia()));
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements ClassBuilder<Thumbnail> {

        private MediaData.Builder mediaData = MediaData.builder();
        private Optional<String> description = Optional.empty();

        /**
         * Sets the {@link Thumbnail} as a spoiler.
         */
        public Builder isSpoiler() {
            return this.isSpoiler(true);
        }

        /**
         * Sets the {@link Thumbnail} as a spoiler.
         *
         * @param value True if spoiler.
         */
        public Builder isSpoiler(boolean value) {
            this.mediaData.isSpoiler(value);
            return this;
        }

        /**
         * Sets the description of the {@link Thumbnail}.
         *
         * @param description The description of the thumbnail.
         */
        public Builder withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        /**
         * Sets the description of the {@link Thumbnail}.
         *
         * @param description The description of the thumbnail.
         * @param args The arguments to format the description with.
         */
        public Builder withDescription(@Nullable @PrintFormat String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        /**
         * Sets the description of the {@link Thumbnail}.
         *
         * @param description The description of the thumbnail.
         */
        public Builder withDescription(@NotNull Optional<String> description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the name of the {@link Thumbnail}.
         *
         * @param name The file name.
         */
        public Builder withName(@NotNull String name) {
            this.mediaData.withName(name);
            return this;
        }

        /**
         * Sets the name of the {@link Thumbnail}.
         *
         * @param name The file name.
         * @param args The arguments to format the name with.
         */
        public Builder withName(@NotNull @PrintFormat String name, @Nullable Object... args) {
            this.mediaData.withName(String.format(name, args));
            return this;
        }

        /**
         * Sets the upload data of the {@link Thumbnail}.
         *
         * @param uploadStream The stream of upload data.
         */
        public Builder withStream(@Nullable InputStream uploadStream) {
            return this.withStream(Optional.ofNullable(uploadStream));
        }

        /**
         * Sets the upload data of the {@link Thumbnail}.
         *
         * @param uploadStream The stream of upload data.
         */
        public Builder withStream(@NotNull Optional<InputStream> uploadStream) {
            this.mediaData.withStream(uploadStream);
            return this;
        }

        /**
         * Sets the url of the {@link Thumbnail}.
         *
         * @param url The url.
         */
        public Builder withUrl(@Nullable String url) {
            return this.withUrl(Optional.ofNullable(url));
        }

        /**
         * Sets the url of the {@link Thumbnail}.
         *
         * @param url The url.
         * @param args The arguments to format the url with.
         */
        public Builder withUrl(@Nullable @PrintFormat String url, @Nullable Object... args) {
            return this.withUrl(StringUtil.formatNullable(url, args));
        }

        /**
         * Sets the url of the {@link Thumbnail}.
         *
         * @param url The url of the thumbnail.
         */
        public Builder withUrl(@NotNull Optional<String> url) {
            this.mediaData.withUrl(url);
            return this;
        }

        private Builder withMediaData(@NotNull MediaData mediaData) {
            this.mediaData = mediaData.mutate();
            return this;
        }

        private Builder withMediaData(@NotNull MediaData.Builder mediaData) {
            this.mediaData = mediaData;
            return this;
        }

        @Override
        public @NotNull Thumbnail build() {
            return new Thumbnail(
                this.mediaData.build(),
                this.description
            );
        }

    }

}
