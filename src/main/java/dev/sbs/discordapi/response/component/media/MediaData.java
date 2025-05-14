package dev.sbs.discordapi.response.component.media;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.NumberUtil;
import dev.sbs.api.util.StringUtil;
import dev.sbs.api.util.builder.annotation.BuildFlag;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.exception.DiscordException;
import discord4j.core.object.component.UnfurledMediaItem;
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
import java.util.UUID;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class MediaData {

    private final int componentId;
    private final String name;
    private final boolean spoiler;
    private final @NotNull String url;
    private final @NotNull Optional<InputStream> uploadStream;
    private final @NotNull Optional<String> proxyUrl;
    private final int width;
    private final int height;
    private final @NotNull String contentType;
    private final @NotNull State state;

    public static @NotNull Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        MediaData mediaData = (MediaData) o;

        return new EqualsBuilder()
            .append(this.getComponentId(), mediaData.getComponentId())
            .append(this.getName(), mediaData.getName())
            .append(this.isSpoiler(), mediaData.isSpoiler())
            .append(this.getUrl(), mediaData.getUrl())
            .append(this.getUploadStream(), mediaData.getUploadStream())
            .append(this.getProxyUrl(), mediaData.getProxyUrl())
            .append(this.getWidth(), mediaData.getWidth())
            .append(this.getHeight(), mediaData.getHeight())
            .append(this.getContentType(), mediaData.getContentType())
            .append(this.getState(), mediaData.getState())
            .build();
    }

    public static @NotNull Builder from(@NotNull MediaData mediaData) {
        return builder()
            .withFileId(mediaData.getComponentId())
            .withName(mediaData.getName())
            .isSpoiler(mediaData.isSpoiler())
            .withUrl(mediaData.getUrl())
            .withStream(mediaData.getUploadStream())
            .withProxyUrl(mediaData.getProxyUrl())
            .withWidth(mediaData.getWidth())
            .withHeight(mediaData.getHeight())
            .withContentType(mediaData.getContentType())
            .withState(mediaData.getState());
    }

    public @NotNull MessageCreateFields.File getD4jFile() {
        if (this.getUploadStream().isEmpty())
            throw new DiscordException("The file named %s has already been uploaded.", this.getName());

        return this.isSpoiler() ?
            MessageCreateFields.File.of(this.getName(), this.getUploadStream().orElseThrow()) :
            MessageCreateFields.FileSpoiler.of(this.getName(), this.getUploadStream().orElseThrow());
    }

    public @NotNull UnfurledMediaItem getD4jMediaItem() {
        if (this.getUploadStream().isPresent())
            return UnfurledMediaItem.of(this.getD4jFile());
        else
            return UnfurledMediaItem.of(this.getUrl());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getComponentId())
            .append(this.getName())
            .append(this.isSpoiler())
            .append(this.getUrl())
            .append(this.getUploadStream())
            .append(this.getProxyUrl())
            .append(this.getWidth())
            .append(this.getHeight())
            .append(this.getContentType())
            .append(this.getState())
            .build();
    }

    public boolean isPendingUpload() {
        return this.getUploadStream().isPresent();
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    public @NotNull Builder mutate(@NotNull discord4j.core.object.entity.Attachment d4jAttachment) {
        return mutate()
            .withUrl(d4jAttachment.getUrl())
            .withProxyUrl(d4jAttachment.getProxyUrl())
            .withWidth(d4jAttachment.getWidth().orElse(0))
            .withHeight(d4jAttachment.getHeight().orElse(0))
            .withContentType(d4jAttachment.getContentType())
            .withState(State.LOADED_SUCCESS);
    }

    public @NotNull Builder mutate(@NotNull UnfurledMediaItem d4jMediaItem) {
        return mutate()
            .withUrl(d4jMediaItem.getURL())
            .withProxyUrl(d4jMediaItem.getProxyUrl())
            .withWidth(d4jMediaItem.getWidth())
            .withHeight(d4jMediaItem.getHeight())
            .withContentType(d4jMediaItem.getContentType())
            .withState(State.LOADED_SUCCESS);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements dev.sbs.api.util.builder.Builder<MediaData> {

        private int fileId = NumberUtil.rand(0);
        private String name = UUID.randomUUID().toString();
        private boolean spoiler = false;
        private Optional<String> proxyUrl = Optional.empty();
        private int width = 0;
        private int height = 0;
        private Optional<String> contentType = Optional.empty();
        private State state = State.LOADING;

        @BuildFlag(notEmpty = true, group = "file")
        private Optional<String> url = Optional.empty();
        @BuildFlag(notEmpty = true, group = "file")
        private Optional<InputStream> uploadStream = Optional.empty();

        public Builder isSpoiler() {
            return this.isSpoiler(true);
        }

        public Builder isSpoiler(boolean value) {
            this.spoiler = value;
            return this;
        }

        public Builder withContentType(@Nullable String contentType) {
            return this.withContentType(Optional.ofNullable(contentType));
        }

        public Builder withContentType(@NotNull Optional<String> contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder withFileId(int fileId) {
            this.fileId = fileId;
            return this;
        }

        public Builder withHeight(int height) {
            this.height = height;
            return this;
        }

        public Builder withName(@NotNull String name) {
            this.name = name;
            return this;
        }

        public Builder withName(@NotNull @PrintFormat String name, @Nullable Object... args) {
            this.name = String.format(name, args);
            return this;
        }

        public Builder withProxyUrl(@Nullable String proxyUrl) {
            return this.withProxyUrl(Optional.ofNullable(proxyUrl));
        }

        public Builder withProxyUrl(@NotNull Optional<String> proxyUrl) {
            this.proxyUrl = proxyUrl;
            return this;
        }

        public Builder withState(@NotNull State state) {
            this.state = state;
            return this;
        }

        public Builder withStream(@Nullable InputStream uploadStream) {
            return this.withStream(Optional.ofNullable(uploadStream));
        }

        public Builder withStream(@NotNull Optional<InputStream> uploadStream) {
            this.uploadStream = uploadStream;
            return this;
        }

        public Builder withUrl(@Nullable String url) {
            return this.withUrl(Optional.ofNullable(url));
        }

        public Builder withUrl(@Nullable @PrintFormat String url, @Nullable Object... args) {
            return this.withUrl(StringUtil.formatNullable(url, args));
        }

        public Builder withUrl(@NotNull Optional<String> url) {
            this.url = url;
            return this;
        }

        public Builder withWidth(int width) {
            this.width = width;
            return this;
        }

        @Override
        public @NotNull MediaData build() {
            Reflection.validateFlags(this);

            return new MediaData(
                this.fileId,
                this.name,
                this.spoiler,
                this.url.orElse(""),
                this.uploadStream,
                this.proxyUrl,
                this.width,
                this.height,
                this.contentType.orElse("application/octet-stream"),
                this.state
            );
        }

    }

    @Getter
    @RequiredArgsConstructor
    public enum State {

        UNKNOWN(0),
        LOADING(1),
        LOADED_SUCCESS(2),
        LOADED_NOT_FOUND(3);

        private final int value;

        public static @NotNull State of(int value) {
            return switch (value) {
                case 1 -> LOADING;
                case 2 -> LOADED_SUCCESS;
                case 3 -> LOADED_NOT_FOUND;
                default -> UNKNOWN;
            };
        }

    }

}
