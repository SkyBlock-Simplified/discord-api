package dev.sbs.discordapi.handler;

import dev.sbs.discordapi.response.Emoji;
import lombok.Data;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Function;

@Data
public final class EmojiHandler {

    @Setter private static @NotNull Function<String, Optional<Emoji>> locator = __ -> Optional.empty();

    public static @NotNull Optional<Emoji> getEmoji(@NotNull String key) {
        return locator.apply(key);
    }

}
