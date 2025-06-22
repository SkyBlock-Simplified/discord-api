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

@Getter
public final class EmojiHandler extends DiscordReference {

    private final @NotNull ConcurrentSet<ResourceEmoji> resourceEmojis;
    private @NotNull ConcurrentList<Emoji> emojis = Concurrent.newList();

    public EmojiHandler(@NotNull DiscordBot discordBot) {
        super(discordBot);

        this.resourceEmojis = this.getDiscordBot()
            .getConfig()
            .getEmojis()
            .stream()
            .map(ResourceEmoji::new)
            .collect(Concurrent.toUnmodifiableSet());
    }

    public void reload() {
        this.emojis = this.getDiscordBot()
            .getGateway()
            .getApplicationInfo()
            .flatMapMany(ApplicationInfo::getEmojis)
            .toStream()
            .map(Emoji::of)
            .collect(Concurrent.toUnmodifiableList());
    }

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

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ResourceEmoji {

        private final @NotNull ResourceInfo resourceInfo;
        private final @NotNull String name;
        private final @NotNull String extension;
        private final @NotNull Image.Format format;

        private ResourceEmoji(@NotNull ResourceInfo resourceInfo) {
            this.resourceInfo = resourceInfo;
            this.name = getName(resourceInfo);
            this.extension = resourceInfo.getExtension();
            this.format = fromExtension(this.extension);
        }

        private static @NotNull String getName(@NotNull ResourceInfo resourceInfo) {
            return StringUtil.stripStart(resourceInfo.getResourceName(), "/")
                .replace('/', '_')
                .replaceFirst("^resources/", "")
                .toUpperCase();
        }

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
