package dev.sbs.discordapi.response;

import dev.sbs.api.client.impl.sbs.response.SkyBlockEmojisResponse;
import dev.sbs.api.data.model.discord.emojis.EmojiModel;
import dev.sbs.api.data.model.skyblock.profiles.ProfileModel;
import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.context.reaction.ReactionContext;
import dev.sbs.discordapi.util.exception.DiscordException;
import discord4j.common.util.Snowflake;
import discord4j.core.object.reaction.ReactionEmoji;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Function;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class Emoji {

    private static final Function<ReactionContext, Mono<Void>> NOOP_HANDLER = __ -> Mono.empty();
    private final @NotNull Snowflake id;
    private final @NotNull String name;
    private final boolean animated;
    private final @NotNull Optional<String> raw;
    @Getter(AccessLevel.NONE)
    private final Optional<Function<ReactionContext, Mono<Void>>> interaction;

    public abstract @NotNull String asFormat();

    public final @NotNull String asPreSpacedFormat() {
        return " " + this.asFormat();
    }

    public final @NotNull String asSpacedFormat() {
        return this.asFormat() + " ";
    }

    public final @NotNull ReactionEmoji getD4jReaction() {
        return this.getRaw().isPresent() ? ReactionEmoji.unicode(this.getRaw().get()) : ReactionEmoji.of(this.getId().asLong(), this.getName(), this.isAnimated());
    }

    public final @NotNull Function<ReactionContext, Mono<Void>> getInteraction() {
        return this.interaction.orElse(NOOP_HANDLER);
    }

    public abstract @NotNull String getUrl();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Emoji emoji = (Emoji) o;

        return new EqualsBuilder()
            .append(this.getId(), emoji.getId())
            .append(this.getName(), emoji.getName())
            .append(this.isAnimated(), emoji.isAnimated())
            .append(this.getRaw(), emoji.getRaw())
            .build();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getId())
            .append(this.getName())
            .append(this.isAnimated())
            .append(this.getRaw())
            .build();
    }

    public final boolean isUnicode() {
        return this.raw.isPresent();
    }

    public static @NotNull Optional<Emoji> of(@NotNull ProfileModel profileModel) {
        return of(profileModel.getEmoji());
    }

    public static @NotNull Optional<Emoji> of(@Nullable EmojiModel emojiModel) {
        return of(Optional.ofNullable(emojiModel), null);
    }

    public static @NotNull Optional<Emoji> of(@NotNull Optional<EmojiModel> emojiModel) {
        return of(emojiModel, null);
    }

    public static @NotNull Optional<Emoji> of(@Nullable EmojiModel emojiModel, Function<ReactionContext, Mono<Void>> interaction) {
        return of(Optional.ofNullable(emojiModel), interaction);
    }

    public static @NotNull Optional<Emoji> of(@NotNull Optional<EmojiModel> emojiModel, Function<ReactionContext, Mono<Void>> interaction) {
        return emojiModel.map(emoji -> new Custom(Snowflake.of(emoji.getEmojiId()), emoji.getKey(), emoji.isAnimated(), interaction));
    }

    public static @NotNull Emoji of(@NotNull SkyBlockEmojisResponse.Emoji emoji) {
        return of(emoji.getId(), emoji.getName(), emoji.isAnimated());
    }

    public static @NotNull Emoji of(@NotNull ReactionEmoji emoji) {
        return emoji instanceof ReactionEmoji.Custom ? new Custom((ReactionEmoji.Custom) emoji) : new Unicode((ReactionEmoji.Unicode) emoji);
    }

    public static @NotNull Emoji of(long id, @NotNull String name) {
        return of(Snowflake.of(id), name);
    }

    public static @NotNull Emoji of(@NotNull Snowflake id, @NotNull String name) {
        return of(id, name, false);
    }

    public static @NotNull Emoji of(long id, @NotNull String name, boolean animated) {
        return of(Snowflake.of(id), name, animated);
    }

    public static @NotNull Emoji of(@NotNull Snowflake id, @NotNull String name, boolean animated) {
        return of(id, name, animated, null);
    }

    public static @NotNull Emoji of(@NotNull Snowflake id, @NotNull String name, boolean animated, Function<ReactionContext, Mono<Void>> interaction) {
        return new Custom(id, name, animated, interaction);
    }

    public static @NotNull Emoji of(@NotNull String raw) {
        return of(raw, null);
    }

    public static @NotNull Emoji of(@NotNull String raw, Function<ReactionContext, Mono<Void>> interaction) {
        return new Unicode(raw, interaction);
    }

    public static @NotNull Emoji of(@NotNull Emoji reaction, Function<ReactionContext, Mono<Void>> interaction) {
        return reaction.isUnicode() ? new Unicode(reaction.getRaw(), interaction) : of(reaction.getId(), reaction.getName(), reaction.isAnimated(), interaction);
    }

    static class Custom extends Emoji {

        Custom(ReactionEmoji.Custom emoji) {
            this(emoji.getId(), emoji.getName(), emoji.isAnimated(), null);
        }

        Custom(Snowflake id, @NotNull String name, boolean animated, Function<ReactionContext, Mono<Void>> interaction) {
            super(id, name, animated, Optional.empty(), Optional.ofNullable(interaction));
        }

        @Override
        public @NotNull String asFormat() {
            return String.format("<%s:%s:%s>", (this.isAnimated() ? "a" : ""), this.getName(), this.getId().asString());
        }

        @Override
        public @NotNull String getUrl() {
            return String.format("https://cdn.discordapp.com/emojis/%s.webp", this.getId().asLong());
        }

    }

    static class Unicode extends Emoji {

        Unicode(ReactionEmoji.Unicode emoji) {
            this(emoji.getRaw(), null);
        }

        Unicode(@NotNull String raw, Function<ReactionContext, Mono<Void>> interaction) {
            this(Optional.of(raw), interaction);
        }

        Unicode(Optional<String> raw, Function<ReactionContext, Mono<Void>> interaction) {
            super(Snowflake.of(-1), "", false, raw, Optional.ofNullable(interaction));
        }

        @Override
        public @NotNull String asFormat() {
            return this.getRaw().orElse("");
        }

        @Override
        public @NotNull String getUrl() {
            throw SimplifiedException.of(DiscordException.class)
                .withMessage("Unicode emojis have no url!")
                .build();
        }

    }

}
