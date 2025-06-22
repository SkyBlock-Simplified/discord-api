package dev.sbs.discordapi.response;

import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import dev.sbs.discordapi.context.reaction.ReactionContext;
import dev.sbs.discordapi.exception.DiscordException;
import discord4j.common.util.Snowflake;
import discord4j.core.object.emoji.CustomEmoji;
import discord4j.core.object.emoji.UnicodeEmoji;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
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

    public final @NotNull discord4j.core.object.emoji.Emoji getD4jReaction() {
        return this.getRaw().isPresent() ? UnicodeEmoji.of(this.getRaw().get()) : CustomEmoji.of(this.getId().asLong(), this.getName(), this.isAnimated());
    }

    public final @NotNull Function<ReactionContext, Mono<Void>> getInteraction() {
        return this.interaction.orElse(NOOP_HANDLER);
    }

    public abstract @NotNull String getUrl();

    public static @NotNull String getUrl(long snowflake) {
        return getUrl(snowflake, false);
    }

    public static @NotNull String getUrl(long snowflake, boolean animated) {
        return String.format("https://cdn.discordapp.com/emojis/%s.%s", snowflake, (animated ? "gif" : "webp"));
    }

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

    public static @NotNull Emoji of(@NotNull discord4j.core.object.emoji.Emoji emoji) {
        return emoji instanceof CustomEmoji ? new Custom((CustomEmoji) emoji) : new Unicode((UnicodeEmoji) emoji);
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

        Custom(CustomEmoji emoji) {
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
            return Emoji.getUrl(this.getId().asLong(), this.isAnimated());
        }

    }

    static class Unicode extends Emoji {

        Unicode(UnicodeEmoji emoji) {
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
            throw new DiscordException("Unicode emojis have no url.");
        }

    }

}
