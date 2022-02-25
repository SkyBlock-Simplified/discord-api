package dev.sbs.discordapi.response;

import dev.sbs.api.data.model.discord.emojis.EmojiModel;
import dev.sbs.api.data.model.skyblock.profiles.ProfileModel;
import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.discordapi.context.message.interaction.reaction.ReactionContext;
import dev.sbs.discordapi.util.exception.DiscordException;
import discord4j.common.util.Snowflake;
import discord4j.core.object.reaction.ReactionEmoji;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class Emoji {

    @Getter private final Snowflake id;
    @Getter private final String name;
    @Getter private final boolean animated;
    @Getter private final Optional<String> raw;
    @Getter private final Optional<Consumer<ReactionContext>> interaction;

    public abstract String asFormat();

    public final String asSpacedFormat() {
        return this.asFormat() + " ";
    }

    public final ReactionEmoji getD4jReaction() {
        return this.getRaw().isPresent() ? ReactionEmoji.unicode(this.getRaw().get()) : ReactionEmoji.of(this.getId().asLong(), this.getName(), this.isAnimated());
    }

    public abstract String getUrl();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Emoji emoji = (Emoji) o;

        return new EqualsBuilder()
            .append(this.isAnimated(), emoji.isAnimated())
            .append(this.getId(), emoji.getId())
            .append(this.getName(), emoji.getName())
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

    public static Optional<Emoji> of(@NotNull ProfileModel profileModel) {
        return of(profileModel.getEmoji());
    }

    public static Optional<Emoji> of(@Nullable EmojiModel emojiModel) {
        return of(Optional.ofNullable(emojiModel), null);
    }

    public static Optional<Emoji> of(@NotNull Optional<EmojiModel> emojiModel) {
        return of(emojiModel, null);
    }

    public static Optional<Emoji> of(@Nullable EmojiModel emojiModel, Consumer<ReactionContext> interaction) {
        return of(Optional.ofNullable(emojiModel), interaction);
    }

    public static Optional<Emoji> of(@NotNull Optional<EmojiModel> emojiModel, Consumer<ReactionContext> interaction) {
        return emojiModel.map(emoji -> new Custom(Snowflake.of(emoji.getEmojiId()), emoji.getKey(), emoji.isAnimated(), interaction));
    }

    public static Emoji of(@NotNull ReactionEmoji emoji) {
        return emoji instanceof ReactionEmoji.Custom ? new Custom((ReactionEmoji.Custom) emoji) : new Unicode((ReactionEmoji.Unicode) emoji);
    }

    public static Emoji of(long id, @NotNull String name) {
        return of(Snowflake.of(id), name);
    }

    public static Emoji of(@NotNull Snowflake id, @NotNull String name) {
        return of(id, name, false);
    }

    public static Emoji of(long id, @NotNull String name, boolean animated) {
        return of(Snowflake.of(id), name, animated);
    }

    public static Emoji of(@NotNull Snowflake id, @NotNull String name, boolean animated) {
        return of(id, name, animated, null);
    }

    public static Emoji of(@NotNull Snowflake id, @NotNull String name, boolean animated, Consumer<ReactionContext> interaction) {
        return new Custom(id, name, animated, interaction);
    }

    public static Emoji of(@NotNull String raw) {
        return of(raw, null);
    }

    public static Emoji of(@NotNull String raw, Consumer<ReactionContext> interaction) {
        return new Unicode(raw, interaction);
    }

    public static Emoji of(@NotNull Emoji reaction, Consumer<ReactionContext> interaction) {
        return reaction.isUnicode() ? new Unicode(reaction.getRaw(), interaction) : of(reaction.getId(), reaction.getName(), reaction.isAnimated(), interaction);
    }

    static class Custom extends Emoji {

        Custom(ReactionEmoji.Custom emoji) {
            this(emoji.getId(), emoji.getName(), emoji.isAnimated(), null);
        }

        Custom(Snowflake id, @NotNull String name, boolean animated, Consumer<ReactionContext> interaction) {
            super(id, name, animated, Optional.empty(), Optional.ofNullable(interaction));
        }

        @Override
        public String asFormat() {
            return FormatUtil.format("<{0}:{1}:{2}>", (this.isAnimated() ? "a" : ""), this.getName(), this.getId().asString());
        }

        @Override
        public String getUrl() {
            return FormatUtil.format("https://cdn.discordapp.com/emojis/{0,number,#}.webp", this.getId().asLong());
        }

    }

    static class Unicode extends Emoji {

        Unicode(ReactionEmoji.Unicode emoji) {
            this(emoji.getRaw(), null);
        }

        Unicode(@NotNull String raw, Consumer<ReactionContext> interaction) {
            this(Optional.of(raw), interaction);
        }

        Unicode(Optional<String> raw, Consumer<ReactionContext> interaction) {
            super(Snowflake.of(-1), null, false, raw, Optional.ofNullable(interaction));
        }

        @Override
        public String asFormat() {
            return this.getRaw().orElse("");
        }

        @Override
        public String getUrl() {
            throw SimplifiedException.of(DiscordException.class)
                .withMessage("Unicode emojis have no url!")
                .build();
        }

    }

}
