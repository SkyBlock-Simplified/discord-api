package dev.sbs.discordapi.response.component.media;

import dev.sbs.api.builder.ClassBuilder;
import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import dev.sbs.api.util.StringUtil;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.type.ContainerComponent;
import dev.sbs.discordapi.response.component.type.TopLevelMessageComponent;
import discord4j.core.spec.MessageCreateFields;
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
public final class Attachment implements Component, TopLevelMessageComponent, ContainerComponent {

    private final @NotNull MediaData mediaData;
    private final long fileId;
    private final long size;

    public static @NotNull Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Attachment that = (Attachment) o;

        return new EqualsBuilder()
            .append(this.getMediaData(), that.getMediaData())
            .append(this.getFileId(), that.getFileId())
            .append(this.getSize(), that.getSize())
            .build();
    }

    public static @NotNull Builder from(@NotNull Attachment attachment) {
        return builder()
            .withMediaData(attachment.getMediaData())
            .withFileId(attachment.getFileId())
            .withSize(attachment.getSize());
    }

    public static @NotNull Builder from(@NotNull discord4j.core.object.entity.Attachment d4jAttachment) {
        return builder()
            .withName(d4jAttachment.getFilename())
            .withUrl(d4jAttachment.getUrl())
            .build()
            .mutate(d4jAttachment);
    }

    @Override
    public @NotNull discord4j.core.object.component.File getD4jComponent() {
        return discord4j.core.object.component.File.of(
            this.getMediaData().getComponentId(),
            this.getMediaData().getD4jMediaItem(),
            this.getMediaData().isSpoiler()
        );
    }

    public @NotNull MessageCreateFields.File getD4jFile() {
        return this.getMediaData().getD4jFile();
    }

    @Override
    public @NotNull Type getType() {
        return Type.FILE;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getMediaData())
            .build();
    }

    public boolean isPendingUpload() {
        return this.getMediaData().isPendingUpload();
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    public @NotNull Builder mutate(@NotNull discord4j.core.object.entity.Attachment d4jAttachment) {
        return from(this)
            .withMediaData(this.getMediaData().mutate(d4jAttachment))
            .withFileId(d4jAttachment.getId().asLong())
            .withSize(d4jAttachment.getSize());
    }

    public @NotNull Builder mutate(@NotNull discord4j.core.object.component.File d4jFile) {
        return from(this)
            .withMediaData(this.getMediaData().mutate(d4jFile.getFile()));
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements ClassBuilder<Attachment> {

        private MediaData.Builder mediaData = MediaData.builder();
        private long fileId = 0L;
        private long size = 0;

        /**
         * Sets the {@link Attachment} as a spoiler.
         */
        public Builder isSpoiler() {
            return this.isSpoiler(true);
        }

        /**
         * Sets the {@link Attachment} as a spoiler.
         *
         * @param value True if spoiler.
         */
        public Builder isSpoiler(boolean value) {
            this.mediaData.isSpoiler(value);
            return this;
        }

        public Builder withFileId(long id) {
            this.fileId = id;
            return this;
        }

        /**
         * Sets the name of the {@link Attachment}.
         *
         * @param name The name of the attachment.
         */
        public Builder withName(@NotNull String name) {
            this.mediaData.withName(name);
            return this;
        }

        /**
         * Sets the name of the {@link Attachment}.
         *
         * @param name The name of the attachment.
         * @param args The arguments to format the name with.
         */
        public Builder withName(@NotNull @PrintFormat String name, @Nullable Object... args) {
            this.mediaData.withName(String.format(name, args));
            return this;
        }

        public Builder withSize(long size) {
            this.size = size;
            return this;
        }

        /**
         * Sets the upload data of the {@link Attachment}.
         *
         * @param uploadStream The stream of attachment data.
         */
        public Builder withStream(@Nullable InputStream uploadStream) {
            return this.withStream(Optional.ofNullable(uploadStream));
        }

        /**
         * Sets the upload data of the {@link Attachment}.
         *
         * @param uploadStream The stream of attachment data.
         */
        public Builder withStream(@NotNull Optional<InputStream> uploadStream) {
            this.mediaData.withStream(uploadStream);
            return this;
        }

        /**
         * Sets the url of the {@link Attachment}.
         *
         * @param url The url of the attachment.
         */
        public Builder withUrl(@Nullable String url) {
            return this.withUrl(Optional.ofNullable(url));
        }

        /**
         * Sets the url of the {@link Attachment}.
         *
         * @param url The url of the attachment.
         * @param args The arguments to format the url with.
         */
        public Builder withUrl(@Nullable @PrintFormat String url, @Nullable Object... args) {
            return this.withUrl(StringUtil.formatNullable(url, args));
        }

        /**
         * Sets the url of the {@link Attachment}.
         *
         * @param url The url of the attachment.
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
        public @NotNull Attachment build() {
            return new Attachment(
                this.mediaData.build(),
                this.fileId,
                this.size
            );
        }

    }

}
