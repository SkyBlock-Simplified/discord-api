package dev.sbs.discordapi.handler;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.util.DiscordReference;
import discord4j.core.object.entity.ApplicationInfo;
import discord4j.core.spec.ApplicationEmojiCreateSpec;
import discord4j.rest.util.Image;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

@Getter
public final class EmojiHandler extends DiscordReference {

    private @NotNull ConcurrentList<Emoji> emojis = new ConcurrentList<>();

    public EmojiHandler(@NotNull DiscordBot discordBot) {
        super(discordBot);
        this.reload();
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
                Reflection.getResources()
                    .getResources()
                    .stream()
                    .filter(resourceInfo -> this.getEmojis()
                        .stream()
                        .noneMatch(emoji -> emoji.getName().equalsIgnoreCase(getName(resourceInfo.getResourceName())))
                    )
                    .map(resourceInfo -> ApplicationEmojiCreateSpec.builder()
                        .name(StringUtil.stripStart(resourceInfo.getResourceName(), "/").replace('/', '_'))
                        .image(Image.ofRaw(
                            resourceInfo.toBytes(),
                            fromExtension(resourceInfo.getExtension())
                        ))
                        .build()
                    )
                    .map(applicationInfo::createEmoji))
                .flatMap(Function.identity())
            )
            .then();
    }

    private static @NotNull String getName(@NotNull String resourceName) {
        return StringUtil.stripStart(resourceName, "/")
            .replace('/', '_')
            .replaceFirst("^resources/", "");
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
