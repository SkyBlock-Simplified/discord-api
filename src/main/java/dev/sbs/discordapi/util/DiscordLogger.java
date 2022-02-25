package dev.sbs.discordapi.util;

import ch.qos.logback.classic.Level;
import dev.sbs.api.util.ConsoleLogger;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.discordapi.DiscordBot;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.MessageChannel;
import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public class DiscordLogger extends ConsoleLogger {

    @Getter(AccessLevel.PRIVATE)
    private final DiscordBot discordBot;

    private final ConcurrentMap<Snowflake, Level> guildLevel = Concurrent.newMap();
    private final ConcurrentMap<Snowflake, Level> channelLevel = Concurrent.newMap();
    private final ConcurrentMap<Snowflake, Level> memberLevel = Concurrent.newMap();

    public DiscordLogger(DiscordBot discordBot, Class<?> tClass) {
        super(tClass);
        this.discordBot = discordBot;
    }

    public DiscordLogger(DiscordBot discordBot, String name) {
        super(name);
        this.discordBot = discordBot;
    }

    public Level getChannelLevel(@NotNull GuildChannel guildChannel) {
        return this.getChannelLevel(guildChannel.getId());
    }

    public Level getChannelLevel(@NotNull MessageChannel messageChannel) {
        return this.getChannelLevel(messageChannel.getId());
    }

    public Level getChannelLevel(@NotNull Snowflake channelId) {
        return this.channelLevel.getOrDefault(channelId, this.getLevel());
    }

    public Level getGuildLevel(@NotNull Guild guild) {
        return this.getGuildLevel(guild.getId());
    }

    public Level getGuildLevel(@NotNull GuildChannel guildChannel) {
        return this.getGuildLevel(guildChannel.getGuildId());
    }

    public Level getGuildLevel(@NotNull Snowflake guildId) {
        return this.guildLevel.getOrDefault(guildId, this.getLevel());
    }

    public Level getMemberLevel(@NotNull User user) {
        return this.getMemberLevel(user.getId());
    }

    public Level getMemberLevel(@NotNull Member member) {
        return this.getMemberLevel(member.getId());
    }

    public Level getMemberLevel(@NotNull Snowflake memberId) {
        return this.memberLevel.getOrDefault(memberId, this.getLevel());
    }

    public void setChannelLevel(@NotNull GuildChannel guildChannel, Level level) {
        this.setChannelLevel(guildChannel.getId(), level);
    }

    public void setChannelLevel(@NotNull MessageChannel messageChannel, Level level) {
        this.setChannelLevel(messageChannel.getId(), level);
    }

    public void setChannelLevel(@NotNull Snowflake channelId, Level level) {
        this.channelLevel.put(channelId, level);
    }

    public void setGuildLevel(@NotNull Guild guild, Level level) {
        this.setGuildLevel(guild.getId(), level);
    }

    public void setGuildLevel(@NotNull GuildChannel guildChannel, Level level) {
        this.setGuildLevel(guildChannel.getGuildId(), level);
    }

    public void setGuildLevel(@NotNull Snowflake guildId, Level level) {
        this.guildLevel.put(guildId, level);
    }

    public void setMemberLevel(@NotNull User user, Level level) {
        this.setMemberLevel(user.getId(), level);
    }

    public void setMemberLevel(@NotNull Member member, Level level) {
        this.setMemberLevel(member.getId(), level);
    }

    public void setMemberLevel(@NotNull Snowflake memberId, Level level) {
        this.memberLevel.put(memberId, level);
    }

}
