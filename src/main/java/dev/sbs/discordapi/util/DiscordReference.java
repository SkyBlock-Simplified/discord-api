package dev.sbs.discordapi.util;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.linked.ConcurrentLinkedMap;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.reference.CommandReference;
import dev.sbs.discordapi.command.reference.SlashCommandReference;
import dev.sbs.discordapi.response.Emoji;
import discord4j.common.util.Snowflake;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.GuildChannel;
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

@SuppressWarnings("rawtypes")
public abstract class DiscordReference {

    @Getter(AccessLevel.PROTECTED)
    private final @NotNull DiscordBot discordBot;
    @Getter private final @NotNull Logger log;

    protected DiscordReference(@NotNull DiscordBot discordBot) {
        this.discordBot = discordBot;
        this.log = LogManager.getLogger(this);
    }

    public @NotNull String capitalizeEnum(@NotNull Enum<?> value) {
        return capitalizeFully(value.name());
    }

    public @NotNull String capitalizeFully(@NotNull String value) {
        return StringUtil.capitalizeFully(StringUtil.defaultIfEmpty(value, "").replace("_", " "));
    }

    protected @NotNull <T extends Annotation> Optional<T> getAnnotation(@NotNull Class<T> tClass, @NotNull Class<? extends CommandReference> command) {
        return command.isAnnotationPresent(tClass) ? Optional.of(command.getAnnotation(tClass)) : java.util.Optional.empty();
    }

    protected final @NotNull Mono<Guild> getGuild(@NotNull Snowflake guildId) {
        return this.getDiscordBot().getGateway().getGuildById(guildId);
    }

    public static @NotNull Optional<Emoji> getEmoji(@NotNull String key) {
        return DiscordConfig.getEmojiLocator().flatMap(function -> function.apply(key));
    }

    public static @NotNull String getEmojiAsFormat(@NotNull String key) {
        return getEmojiAsFormat(key, "");
    }

    public static @NotNull String getEmojiAsFormat(@NotNull String key, @NotNull String defaultValue) {
        return getEmoji(key).map(Emoji::asFormat).orElse(defaultValue);
    }

    // --- Command Searching ---

    protected final @NotNull Optional<CommandReference<?>> getCommandById(long commandId) {
        return this.getDiscordBot()
            .getCommandRegistrar()
            .getLoadedCommands()
            .stream()
            .filter(command -> command.getId() == commandId)
            .findFirst();
    }

    protected final @NotNull ConcurrentList<ApplicationCommandInteractionOption> getCommandOptionData(@NotNull SlashCommandReference slashCommand, @NotNull List<ApplicationCommandInteractionOption> commandOptions) {
        ConcurrentList<ApplicationCommandInteractionOption> options = Concurrent.newList(commandOptions);
        ConcurrentList<String> commandTree = Concurrent.newList(slashCommand.getCommandTree());
        commandTree.removeFirst();

        while (commandTree.notEmpty()) {
            for (ApplicationCommandInteractionOption option : options) {
                if (option.getName().equals(commandTree.get(0))) {
                    commandTree.removeFirst();
                    options = Concurrent.newList(option.getOptions());
                }
            }
        }

        return options;
    }

    // --- Permissions ---
    protected final @NotNull ConcurrentLinkedMap<Permission, Boolean> getGuildPermissionMap(@NotNull Snowflake userId, @NotNull Optional<Guild> guild, @NotNull Permission... permissions) {
        return this.getGuildPermissionMap(userId, guild, Arrays.asList(permissions));
    }

    protected final @NotNull ConcurrentLinkedMap<Permission, Boolean> getGuildPermissionMap(@NotNull Snowflake userId, @NotNull Optional<Guild> optionalGuild, @NotNull Iterable<Permission> permissions) {
        return optionalGuild.map(guild -> guild.getMemberById(userId))
            .orElse(Mono.empty())
            .flatMap(Member::getBasePermissions)
            .map(permissionSet -> this.getPermissionMap(permissionSet, permissions))
            .blockOptional()
            .orElse(Concurrent.newLinkedMap());
    }

    protected final @NotNull ConcurrentLinkedMap<Permission, Boolean> getChannelPermissionMap(@NotNull Snowflake userId, @NotNull Mono<GuildChannel> channel, @NotNull Permission... permissions) {
        return this.getChannelPermissionMap(userId, channel, Arrays.asList(permissions));
    }

    protected final @NotNull ConcurrentLinkedMap<Permission, Boolean> getChannelPermissionMap(@NotNull Snowflake userId, @NotNull Mono<GuildChannel> channel, @NotNull Iterable<Permission> permissions) {
        return channel.flatMap(chl -> chl.getEffectivePermissions(userId))
            .map(permissionSet -> this.getPermissionMap(permissionSet, permissions))
            .blockOptional()
            .orElse(Concurrent.newLinkedMap());
    }

    private @NotNull ConcurrentLinkedMap<Permission, Boolean> getPermissionMap(@NotNull PermissionSet permissionSet, @NotNull Iterable<Permission> permissions) {
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
                .orElse(applicationInfoData.owner().id().asLong() == userId.asLong())
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
