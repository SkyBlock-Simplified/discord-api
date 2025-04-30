package dev.sbs.discordapi.response;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.NumberUtil;
import dev.sbs.api.util.StringUtil;
import dev.sbs.api.util.builder.annotation.BuildFlag;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.exception.DiscordException;
import dev.sbs.discordapi.response.component.type.MessageComponent;
import dev.sbs.discordapi.response.component.type.TopLevelMessageComponent;
import dev.sbs.discordapi.response.component.type.v2.ContainerComponent;
import discord4j.core.spec.MessageCreateFields;
import discord4j.discordjson.json.ImmutableComponentData;
import discord4j.discordjson.json.UnfurledMediaItemData;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Attachment implements MessageComponent, TopLevelMessageComponent, ContainerComponent {

    // File Details
    private final @NotNull String identifier;
    private final int fileId;
    private final boolean spoiler;
    private final @NotNull String url;
    private final @NotNull String name;
    private final @NotNull Optional<InputStream> uploadStream;
    private final @NotNull Attachment.State state;

    // Attachment Details
    private final @NotNull OptionalLong attachmentId;
    private final @NotNull Optional<String> description;
    private final @NotNull OptionalLong size;

    // Uploaded Details
    private final @NotNull Optional<String> contentType;
    private final @NotNull Optional<String> proxyUrl;
    private final @NotNull OptionalInt height;
    private final @NotNull OptionalInt width;

    public static @NotNull Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Attachment that = (Attachment) o;

        return new EqualsBuilder()
            .append(this.getFileId(), that.getFileId())
            .append(this.isSpoiler(), that.isSpoiler())
            .append(this.getIdentifier(), that.getIdentifier())
            .append(this.getUrl(), that.getUrl())
            .append(this.getName(), that.getName())
            .append(this.getUploadStream(), that.getUploadStream())
            .append(this.getState(), that.getState())
            .append(this.getAttachmentId(), that.getAttachmentId())
            .append(this.getDescription(), that.getDescription())
            .append(this.getSize(), that.getSize())
            .append(this.getContentType(), that.getContentType())
            .append(this.getProxyUrl(), that.getProxyUrl())
            .append(this.getHeight(), that.getHeight())
            .append(this.getWidth(), that.getWidth())
            .build();
    }

    public static @NotNull Builder from(@NotNull Attachment attachment) {
        return builder()
            .isSpoiler(attachment.isSpoiler())
            .withFileId(attachment.getFileId())
            .withIdentifier(attachment.getIdentifier())
            .withName(attachment.getName())
            .withUrl(attachment.getUrl())
            .withStream(attachment.getUploadStream())
            .withState(attachment.getState())
            .withAttachmentId(attachment.getAttachmentId())
            .withDescription(attachment.getDescription())
            .withSize(attachment.getSize())
            .withContentType(attachment.getContentType())
            .withProxyUrl(attachment.getProxyUrl())
            .withHeight(attachment.getHeight())
            .withWidth(attachment.getWidth());
    }

    public static @NotNull Attachment from(@NotNull String identifier, @NotNull discord4j.core.object.entity.Attachment d4jAttachment) {
        return builder()
            .withIdentifier(identifier)
            .build()
            .mutate(d4jAttachment)
            .build();
    }

    public @NotNull MessageCreateFields.File getD4jFile() {
        if (this.getState() == State.LOADED_SUCCESS)
            throw new DiscordException("The file '%s' is already uploaded.", this.getName());

        return this.isSpoiler() ?
            MessageCreateFields.File.of(this.getName(), this.getUploadStream().orElseThrow()) :
            MessageCreateFields.FileSpoiler.of(this.getName(), this.getUploadStream().orElseThrow());
    }

    @Override
    public @NotNull discord4j.core.object.component.File getD4jComponent() {
        return Reflection.of(discord4j.core.object.component.File.class).newInstance(
            ImmutableComponentData.builder()
                .type(this.getType().getValue())
                .id(this.getFileId())
                .file(UnfurledMediaItemData.builder().url(this.getUrl()).build())
                .spoiler(this.isSpoiler())
                .build()
        );
    }

    @Override
    public @NotNull Type getType() {
        return Type.FILE;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getIdentifier())
            .append(this.getFileId())
            .append(this.isSpoiler())
            .append(this.getUrl())
            .append(this.getName())
            .append(this.getUploadStream())
            .append(this.getState())
            .append(this.getAttachmentId())
            .append(this.getDescription())
            .append(this.getSize())
            .append(this.getContentType())
            .append(this.getProxyUrl())
            .append(this.getHeight())
            .append(this.getWidth())
            .build();
    }

    public boolean isPendingUpload() {
        return this.getUploadStream().isPresent();
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    public @NotNull Builder mutate(@NotNull discord4j.core.object.entity.Attachment d4jAttachment) {
        return from(this)
            .withUrl(d4jAttachment.getUrl())
            .withName(d4jAttachment.getFilename())
            .withStream(Optional.empty())
            .withState(State.LOADED_SUCCESS)
            // Attachment Details
            .withAttachmentId(OptionalLong.of(d4jAttachment.getId().asLong()))
            .withDescription(this.getDescription().or(() -> d4jAttachment.getData().description().toOptional()))
            .withSize(OptionalLong.of(d4jAttachment.getSize()))
            // Uploaded Details
            .withContentType(d4jAttachment.getContentType())
            .withProxyUrl(Optional.of(d4jAttachment.getProxyUrl()))
            .withHeight(d4jAttachment.getHeight())
            .withWidth(d4jAttachment.getWidth());
    }

    public @NotNull Builder mutate(@NotNull discord4j.core.object.component.File d4jFile) {
        return from(this)
            .withUrl(d4jFile.getFile().getURL())
            .withName(d4jFile.getFile().getURL().substring(d4jFile.getFile().getURL().lastIndexOf('/') + 1))
            .withStream(Optional.empty())
            .withState(State.LOADED_SUCCESS)
            // Attachment Details
            .withAttachmentId(OptionalLong.empty())
            .withDescription(this.getDescription().or(() -> d4jFile.getData().description().toOptional().flatMap(optional -> optional)))
            .withSize(OptionalLong.empty())
            // Uploaded Details
            .withContentType(Optional.of(d4jFile.getFile().getContentType()))
            .withProxyUrl(d4jFile.getFile().getProxyUrl())
            .withHeight(OptionalInt.of(d4jFile.getFile().getHeight()))
            .withWidth(OptionalInt.of(d4jFile.getFile().getWidth()));
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements dev.sbs.api.util.builder.Builder<Attachment> {

        @BuildFlag(nonNull = true)
        private String identifier = UUID.randomUUID().toString();
        @BuildFlag(notEmpty = true, group = "file")
        private Optional<String> url = Optional.empty();
        @BuildFlag(notEmpty = true)
        private Optional<String> name = Optional.empty();
        @BuildFlag(notEmpty = true, group = "file")
        private Optional<InputStream> uploadStream = Optional.empty();
        private int fileId = NumberUtil.rand(0);
        private boolean spoiler = false;
        private Attachment.State state = State.LOADING;

        // Attachment Details
        private OptionalLong attachmentId = OptionalLong.empty();
        private Optional<String> description = Optional.empty();
        private OptionalLong size = OptionalLong.empty();

        // Uploaded Details
        private Optional<String> contentType = Optional.empty();
        private Optional<String> proxyUrl = Optional.empty();
        private OptionalInt height = OptionalInt.empty();
        private OptionalInt width = OptionalInt.empty();

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
            this.spoiler = value;
            return this;
        }

        /**
         * Sets the file id of the {@link Attachment}.
         * <ul>
         *     <li>Only used for components</li>
         * </ul>
         *
         * @param fileId The file id of the attachment.
         */
        public Builder withFileId(int fileId) {
            this.fileId = fileId;
            return this;
        }

        /**
         * Sets the identifier of the {@link Attachment}.
         *
         * @param identifier The identifier of the attachment.
         */
        public Builder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Sets the name of the {@link Attachment}.
         *
         * @param name The name of the attachment.
         */
        public Builder withName(@Nullable String name) {
            return this.withName(Optional.ofNullable(name));
        }

        /**
         * Sets the name of the {@link Attachment}.
         *
         * @param name The name of the attachment.
         * @param args The arguments to format the name with.
         */
        public Builder withName(@Nullable @PrintFormat String name, @Nullable Object... args) {
            return this.withName(StringUtil.formatNullable(name, args));
        }

        /**
         * Sets the name of the {@link Attachment}.
         *
         * @param name The name of the attachment.
         */
        public Builder withName(@NotNull Optional<String> name) {
            this.name = name;
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
            this.uploadStream = uploadStream;
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
            this.url = url;
            return this;
        }

        /**
         * Sets the state of the {@link Attachment}.
         *
         * @param state The state of the attachment.
         */
        private Builder withState(@NotNull State state) {
            this.state = state;
            return this;
        }

        // Attachment Details
        private Builder withAttachmentId(@Nullable Long attachmentId) {
            return this.withAttachmentId(attachmentId == null ? OptionalLong.empty() : OptionalLong.of(attachmentId));
        }

        private Builder withAttachmentId(@NotNull OptionalLong attachmentId) {
            this.attachmentId = attachmentId;
            return this;
        }

        private Builder withDescription(@NotNull Optional<String> description) {
            this.description = description;
            return this;
        }

        private Builder withSize(@Nullable Long size) {
            return this.withSize(size == null ? OptionalLong.empty() : OptionalLong.of(size));
        }

        private Builder withSize(@NotNull OptionalLong size) {
            this.size = size;
            return this;
        }

        // File Details
        private Builder withContentType(@Nullable String contentType) {
            return this.withContentType(Optional.ofNullable(contentType));
        }

        private Builder withContentType(@NotNull Optional<String> contentType) {
            this.contentType = contentType;
            return this;
        }

        private Builder withProxyUrl(@Nullable String proxyUrl) {
            return this.withProxyUrl(Optional.ofNullable(proxyUrl));
        }

        private Builder withProxyUrl(@NotNull Optional<String> proxyUrl) {
            this.proxyUrl = proxyUrl;
            return this;
        }

        private Builder withHeight(@Nullable Integer height) {
            this.withHeight(height == null ? OptionalInt.empty() : OptionalInt.of(height));
            return this;
        }

        private Builder withHeight(@NotNull OptionalInt height) {
            this.height = height;
            return this;
        }

        private Builder withWidth(@Nullable Integer width) {
            this.withWidth(width == null ? OptionalInt.empty() : OptionalInt.of(width));
            return this;
        }

        private Builder withWidth(@NotNull OptionalInt width) {
            this.width = width;
            return this;
        }

        @Override
        public @NotNull Attachment build() {
            Reflection.validateFlags(this);

            return new Attachment(
                this.identifier,
                this.fileId,
                this.spoiler,
                this.url.orElse(""),
                this.name.orElseThrow(),
                this.uploadStream,
                this.state,
                this.attachmentId,
                this.description,
                this.size,
                this.contentType,
                this.proxyUrl,
                this.height,
                this.width
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

        public static @NotNull Attachment.State of(int value) {
            return switch (value) {
                case 1 -> LOADING;
                case 2 -> LOADED_SUCCESS;
                case 3 -> LOADED_NOT_FOUND;
                default -> UNKNOWN;
            };
        }

    }

}
