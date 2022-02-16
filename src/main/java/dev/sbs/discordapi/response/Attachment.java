package dev.sbs.discordapi.response;

import discord4j.core.spec.MessageCreateFields;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Attachment {

    @Getter private final String name;
    @Getter private final InputStream inputStream;
    @Getter private final boolean spoiler;

    public MessageCreateFields.File getD4jFile() {
        return this.isSpoiler() ? MessageCreateFields.File.of(this.getName(), this.getInputStream()) : MessageCreateFields.FileSpoiler.of(this.getName(), this.getInputStream());
    }

    public static Attachment of(@NotNull String name, @Nullable InputStream inputStream) {
        return of(name, inputStream, false);
    }

    public static Attachment of(@NotNull String name, @Nullable InputStream inputStream, boolean spoiler) {
        return new Attachment(name, inputStream, spoiler);
    }

}
