package dev.simplified.discordapi.response;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Resolves custom {@link Emoji Emojis} by name, decoupled from any {@link dev.simplified.discordapi.DiscordBot bot}.
 *
 * <p>
 * The name to snowflake-id mapping is the only bit of a live application an {@code Emoji} needs - an
 * {@code Emoji} formats and links from its id and name alone. Exposing that mapping through this narrow
 * capability lets pure data classes (a {@link Response}, its pages, its components) render emoji without
 * holding a bot; the renderer supplies a resolver at draw time. {@link dev.simplified.discordapi.handler.EmojiHandler}
 * is the live implementation, and tests or fixtures can supply their own.
 */
public interface EmojiResolver {

    /** Resolver that resolves no custom emoji, so navigation renders label-only. */
    EmojiResolver EMPTY = Concurrent::newUnmodifiableList;

    /** The emojis available for resolution. */
    @NotNull ConcurrentList<Emoji> getEmojis();

    /**
     * Finds the emoji with the given name, ignoring case.
     *
     * @param name the emoji name to resolve
     * @return the matching emoji, or empty if none is registered
     */
    default @NotNull Optional<Emoji> getEmoji(@NotNull String name) {
        return this.getEmojis().matchFirst(emoji -> emoji.getName().equalsIgnoreCase(name));
    }

}
