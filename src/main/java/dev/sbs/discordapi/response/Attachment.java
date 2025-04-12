package dev.sbs.discordapi.response;

import dev.sbs.discordapi.exception.DiscordException;
import discord4j.core.spec.MessageCreateFields;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.util.Optional;
import java.util.OptionalInt;

@Getter
@AllArgsConstructor
public final class Attachment {

    // File Upload
    private final @NotNull String name;
    private final boolean spoiler;
    private @NotNull Optional<InputStream> uploadStream;

    // Uploaded File
    private long id;
    private @NotNull Optional<String> description;
    private @NotNull String contentType;
    private long size;
    private @NotNull String url;
    private @NotNull String proxyUrl;
    private @NotNull OptionalInt height;
    private @NotNull OptionalInt width;

    public @NotNull MessageCreateFields.File getD4jFile() {
        if (this.isUploaded())
            throw new DiscordException("The file '%s' is already uploaded.", this.getName());

        return this.isSpoiler() ?
            MessageCreateFields.File.of(this.getName(), this.getUploadStream().orElseThrow()) :
            MessageCreateFields.FileSpoiler.of(this.getName(), this.getUploadStream().orElseThrow());
    }

    public static @NotNull Attachment of(@NotNull discord4j.core.object.entity.Attachment d4jAttachment) {
        return new Attachment(
            d4jAttachment.getFilename(),
            d4jAttachment.isSpoiler(),
            Optional.empty(),
            d4jAttachment.getId().asLong(),
            d4jAttachment.getData().description().toOptional(),
            d4jAttachment.getContentType().orElseThrow(),
            d4jAttachment.getSize(),
            d4jAttachment.getUrl(),
            d4jAttachment.getProxyUrl(),
            d4jAttachment.getHeight(),
            d4jAttachment.getWidth()
        );
    }

    public static @NotNull Attachment of(@NotNull String name, @NotNull InputStream inputStream) {
        return of(name, inputStream, false);
    }

    public static @NotNull Attachment of(@NotNull String name, @NotNull InputStream inputStream, boolean spoiler) {
        return new Attachment(
            name,
            spoiler,
            Optional.of(inputStream),
            -1,
            Optional.empty(),
            "",
            -1,
            "",
            "",
            OptionalInt.empty(),
            OptionalInt.empty()
        );
    }

    public boolean isUploaded() {
        return this.uploadStream.isEmpty();
    }

    public boolean notUploaded() {
        return !this.isUploaded();
    }

}
