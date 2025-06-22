package dev.sbs.discordapi.util;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.linked.ConcurrentLinkedMap;
import dev.sbs.api.util.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.response.Emoji;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.discordjson.json.ApplicationCommandInteractionData;
import discord4j.discordjson.json.ApplicationCommandInteractionOptionData;
import discord4j.discordjson.json.ApplicationTeamMemberData;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import lombok.AccessLevel;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class DiscordReference {

    @Getter(AccessLevel.PROTECTED)
    private final @NotNull DiscordBot discordBot;
    @Getter private final @NotNull Logger log;

    protected DiscordReference(@NotNull DiscordBot discordBot) {
        this.discordBot = discordBot;
        this.log = LogManager.getLogger(this);
    }

    protected final @NotNull <T extends Annotation> Optional<T> getAnnotation(@NotNull Class<T> aClass, @NotNull Class<?> tClass) {
        return tClass.isAnnotationPresent(aClass) ? Optional.of(tClass.getAnnotation(aClass)) : java.util.Optional.empty();
    }

    protected final @NotNull Optional<Emoji> getEmoji(@NotNull String name) {
        return this.getDiscordBot()
            .getEmojiHandler()
            .getEmojis()
            .matchFirst(emoji -> emoji.getName().equalsIgnoreCase(name));
    }

    protected final boolean matchesInteractionData(@NotNull DiscordCommand<?> command, @NotNull ApplicationCommandInteractionData commandData) {
        if (commandData.name().isAbsent())
            return false;

        String compareName = commandData.name().get();

        if (StringUtil.isNotEmpty(command.getStructure().parent().name())) {
            if (commandData.options().isAbsent() || commandData.options().get().isEmpty())
                return false;

            List<ApplicationCommandInteractionOptionData> options = commandData.options().get();
            ApplicationCommandInteractionOptionData option = options.get(0);

            if (!compareName.equals(command.getStructure().parent().name()))
                return false;

            if (options.get(0).type() > 2)
                return false;

            if (StringUtil.isNotEmpty(command.getStructure().group().name())) {
                if (!option.name().equals(command.getStructure().group().name()))
                    return false;

                if (option.options().isAbsent() || option.options().get().isEmpty())
                    return false;

                options = option.options().get();
                option = options.get(0);
            }

            compareName = option.name();
        }

        return compareName.equals(command.getStructure().name());
    }

    // --- Permissions ---

    protected final @NotNull ConcurrentLinkedMap<Permission, Boolean> getGuildPermissionMap(@NotNull Snowflake userId, @NotNull Optional<Guild> guild, @NotNull Permission... permissions) {
        return this.getGuildPermissionMap(userId, guild, Arrays.asList(permissions));
    }

    protected final @NotNull ConcurrentLinkedMap<Permission, Boolean> getGuildPermissionMap(@NotNull Snowflake userId, @NotNull Optional<Guild> optionalGuild, @NotNull Iterable<Permission> permissions) {
        return optionalGuild.map(guild -> guild.getMemberById(userId))
            .orElse(Mono.empty())
            .flatMap(Member::getBasePermissions)
            .map(permissionSet -> getPermissionMap(permissionSet, permissions))
            .blockOptional()
            .orElse(Concurrent.newLinkedMap());
    }

    protected final @NotNull ConcurrentLinkedMap<Permission, Boolean> getChannelPermissionMap(@NotNull Snowflake userId, @NotNull Mono<GuildChannel> channel, @NotNull Permission... permissions) {
        return this.getChannelPermissionMap(userId, channel, Arrays.asList(permissions));
    }

    protected final @NotNull ConcurrentLinkedMap<Permission, Boolean> getChannelPermissionMap(@NotNull Snowflake userId, @NotNull Mono<GuildChannel> channel, @NotNull Iterable<Permission> permissions) {
        return channel.flatMap(chl -> chl.getEffectivePermissions(userId))
            .map(permissionSet -> getPermissionMap(permissionSet, permissions))
            .blockOptional()
            .orElse(Concurrent.newLinkedMap());
    }

    private static @NotNull ConcurrentLinkedMap<Permission, Boolean> getPermissionMap(@NotNull PermissionSet permissionSet, @NotNull Iterable<Permission> permissions) {
        // Set Default Permissions
        ConcurrentLinkedMap<Permission, Boolean> permissionMap = Concurrent.newLinkedMap();
        permissions.forEach(permission -> permissionMap.put(permission, false));

        // Check Permissions
        for (Permission permission : permissions)
            permissionMap.put(permission, permissionSet.contains(permission));

        return permissionMap;
    }

    // Channel Permissions
    protected final boolean hasChannelPermissions(@NotNull Snowflake userId, @NotNull Mono<GuildChannel> channel, @NotNull Permission... permissions) {
        return this.hasChannelPermissions(userId, channel, Concurrent.newList(permissions));
    }

    protected final boolean hasChannelPermissions(@NotNull Snowflake userId, @NotNull Mono<GuildChannel> channel, @NotNull Iterable<Permission> permissions) {
        return this.getChannelPermissionMap(userId, channel, permissions).stream().allMatch(Map.Entry::getValue);
    }

    // Guild Permissions
    protected final boolean hasGuildPermissions(@NotNull Snowflake userId, @NotNull Optional<Guild> optionalGuild, @NotNull Permission... permissions) {
        return this.hasGuildPermissions(userId, optionalGuild, Arrays.asList(permissions));
    }

    protected final boolean hasGuildPermissions(@NotNull Snowflake userId, @NotNull Optional<Guild> optionalGuild, @NotNull Iterable<Permission> permissions) {
        return this.getGuildPermissionMap(userId, optionalGuild, permissions).stream().allMatch(Map.Entry::getValue);
    }

    // --- Owner Permissions ---
    protected final boolean isDeveloper(@NotNull Snowflake userId) {
        return this.getDiscordBot()
            .getClient()
            .getApplicationInfo()
            .cache()
            .blockOptional()
            .map(applicationInfoData -> applicationInfoData.team()
                .map(applicationTeamData -> {
                    for (ApplicationTeamMemberData teamMemberData : applicationTeamData.members()) {
                        if (teamMemberData.user().id().asLong() == userId.asLong())
                            return true;
                    }

                    return false;
                })
                .orElse(applicationInfoData.owner().map(userData -> userData.id().asLong() == userId.asLong()).toOptional().orElse(false))
            )
            .orElse(false);
    }

    protected final boolean isGuildOwner(@NotNull Snowflake userId, @Nullable Guild guild) {
        return this.isGuildOwner(userId, Optional.ofNullable(guild));
    }

    protected final boolean isGuildOwner(@NotNull Snowflake userId, @NotNull Optional<Guild> optionalGuild) {
        return optionalGuild.map(guild -> guild.getOwnerId().equals(userId)).orElse(false);
    }

}
