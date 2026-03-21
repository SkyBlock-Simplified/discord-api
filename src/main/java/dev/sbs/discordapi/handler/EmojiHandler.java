package dev.sbs.discordapi.handler;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.collection.concurrent.ConcurrentSet;
import dev.sbs.api.reflection.info.ResourceInfo;
import dev.sbs.api.util.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.util.DiscordReference;
import discord4j.core.object.entity.ApplicationInfo;
import discord4j.core.spec.ApplicationEmojiCreateSpec;
import discord4j.rest.util.Image;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * Handler for custom application emojis, managing the discovery of
 * classpath-based emoji resources, uploading new emojis to the Discord
 * application, and caching the current set of registered emojis for
 * lookup by name.
 *
 * @see ResourceEmoji
 * @see Emoji
 */
@Getter
public final class EmojiHandler extends DiscordReference {

    /** Emoji resources discovered from the classpath at construction time. */
    private final @NotNull ConcurrentSet<ResourceEmoji> resourceEmojis;

    /** Cached list of emojis currently registered with the Discord application. */
    private @NotNull ConcurrentList<Emoji> emojis = Concurrent.newList();

    /**
     * Constructs a new {@code EmojiHandler} and resolves classpath emoji
     * resources from the bot's {@link DiscordBot#getConfig() configuration}.
     *
     * @param discordBot the bot this handler belongs to
     */
    public EmojiHandler(@NotNull DiscordBot discordBot) {
        super(discordBot);

        this.resourceEmojis = this.getDiscordBot()
            .getConfig()
            .getEmojis()
            .stream()
            .map(ResourceEmoji::new)
            .collect(Concurrent.toUnmodifiableSet());
    }

    /**
     * Reloads the cached emoji list from the Discord application's
     * currently registered emojis.
     */
    public void reload() {
        this.emojis = this.getDiscordBot()
            .getGateway()
            .getApplicationInfo()
            .flatMapMany(ApplicationInfo::getEmojis)
            .toStream()
            .map(Emoji::of)
            .collect(Concurrent.toUnmodifiableList());
    }

    /**
     * Uploads any classpath emoji resources that are not yet registered
     * with the Discord application, skipping emojis whose names already
     * exist.
     *
     * @return a mono that completes when all new emojis have been uploaded
     */
    public Mono<Void> upload() {
        return this.getDiscordBot()
            .getGateway()
            .getApplicationInfo()
            .flatMapMany(applicationInfo -> Flux.fromStream(
                this.getResourceEmojis()
                    .stream()
                    .filter(resourceEmoji -> this.getEmojis()
                        .stream()
                        .noneMatch(emoji -> emoji.getName().equalsIgnoreCase(resourceEmoji.getName()))
                    )
                    .map(resourceEmoji -> ApplicationEmojiCreateSpec.builder()
                        .name(resourceEmoji.getName())
                        .image(Image.ofRaw(
                            resourceEmoji.getResourceInfo().toBytes(),
                            resourceEmoji.getFormat()
                        ))
                        .build()
                    )
                    .map(applicationInfo::createEmoji))
                .flatMap(Function.identity())
            )
            .then();
    }

    /**
     * Representation of a classpath-sourced emoji image resource, including
     * its derived name, file extension, and {@link Image.Format}.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ResourceEmoji {

        /** Classpath resource metadata for the emoji image file. */
        private final @NotNull ResourceInfo resourceInfo;

        /** Derived emoji name, converted from the resource path. */
        private final @NotNull String name;

        /** File extension of the emoji image (e.g., "png", "gif"). */
        private final @NotNull String extension;

        /** Discord image format corresponding to the file extension. */
        private final @NotNull Image.Format format;

        /**
         * Constructs a new {@code ResourceEmoji} from the given classpath
         * resource, deriving the name, extension, and format automatically.
         *
         * @param resourceInfo the classpath resource metadata
         */
        private ResourceEmoji(@NotNull ResourceInfo resourceInfo) {
            this.resourceInfo = resourceInfo;
            this.name = getName(resourceInfo);
            this.extension = resourceInfo.getExtension();
            this.format = fromExtension(this.extension);
        }

        /**
         * Derives the emoji name from the resource path by stripping the
         * leading slash, replacing path separators with underscores,
         * removing the "resources/" prefix, and uppercasing the result.
         *
         * @param resourceInfo the classpath resource metadata
         * @return the derived emoji name
         */
        private static @NotNull String getName(@NotNull ResourceInfo resourceInfo) {
            return StringUtil.stripStart(resourceInfo.getResourceName(), "/")
                .replace('/', '_')
                .replaceFirst("^resources/", "")
                .toUpperCase();
        }

        /**
         * Maps a file extension string to the corresponding Discord
         * {@link Image.Format}.
         *
         * @param extension the lowercase file extension without a leading dot
         * @return the matching image format, or {@link Image.Format#UNKNOWN}
         *         if unrecognized
         */
        private static @NotNull Image.Format fromExtension(@NotNull String extension) {
            return switch (extension) {
                case "jpg", "jpeg" -> Image.Format.JPEG;
                case "png" -> Image.Format.PNG;
                case "webp" -> Image.Format.WEB_P;
                case "gif" -> Image.Format.GIF;
                default -> Image.Format.UNKNOWN;
            };
        }

    }

}
