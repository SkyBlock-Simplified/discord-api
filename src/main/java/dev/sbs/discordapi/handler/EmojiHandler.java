package dev.sbs.discordapi.handler;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.util.DiscordReference;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Function;

public class EmojiHandler extends DiscordReference {

    private final @NotNull Function<String, Optional<Emoji>> locator;

    public EmojiHandler(@NotNull DiscordBot discordBot, @NotNull Function<String, Optional<Emoji>> locator) {
        super(discordBot);
        this.locator = locator;
    }

    @Override
    public final @NotNull Optional<Emoji> getEmoji(@NotNull String key) {
        return this.locator.apply(key);
    }

}
