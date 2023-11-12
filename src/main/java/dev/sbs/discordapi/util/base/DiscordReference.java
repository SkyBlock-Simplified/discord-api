package dev.sbs.discordapi.util.base;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.linked.ConcurrentLinkedMap;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.CommandId;
import dev.sbs.discordapi.command.reference.CommandReference;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.reaction.Reaction;
import discord4j.discordjson.json.ApplicationCommandInteractionData;
import discord4j.discordjson.json.ApplicationCommandInteractionOptionData;
import discord4j.discordjson.json.ApplicationTeamMemberData;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class DiscordReference {

    protected abstract @NotNull DiscordBot getDiscordBot();

    public static @NotNull String capitalizeEnum(Enum<?> value) {
        return capitalizeFully(value.name());
    }

    public static @NotNull String capitalizeFully(String value) {
        return StringUtil.capitalizeFully(StringUtil.defaultIfEmpty(value, "").replace("_", " "));
    }

    public static @NotNull <T extends Annotation> Optional<T> getAnnotation(@NotNull Class<T> tClass, @NotNull Class<? extends CommandReference> command) {
        return command.isAnnotationPresent(tClass) ? Optional.of(command.getAnnotation(tClass)) : Optional.empty();
    }

    public static @NotNull Optional<CommandId> getCommandId(@NotNull Class<? extends CommandReference> command) {
        return getAnnotation(CommandId.class, command);
    }

    protected final @NotNull Mono<Guild> getGuild(@NotNull Snowflake guildId) {
        return this.getDiscordBot().getGateway().getGuildById(guildId);
    }

    // TODO: This cannot exist as-is
    public static @NotNull Optional<Emoji> getEmoji(@NotNull String key) {
        return Optional.empty();
        //return SimplifiedApi.getRepositoryOf(EmojiModel.class).findFirst(EmojiModel::getKey, key).flatMap(Emoji::of);
    }

    public static @NotNull String getEmojiAsFormat(@NotNull String key) {
        return getEmojiAsFormat(key, "");
    }

    public static @NotNull String getEmojiAsFormat(@NotNull String key, @NotNull String defaultValue) {
        return getEmoji(key).map(Emoji::asFormat).orElse(defaultValue);
    }

    // --- Command Searching ---

    private @NotNull ConcurrentList<String> getCommandTree(@NotNull ApplicationCommandInteractionData commandInteractionData) {
        ConcurrentList<String> commandTree = Concurrent.newList(commandInteractionData.name().toOptional().orElseThrow()); // Should never be NULL

        if (!commandInteractionData.type().isAbsent()) {
            if (!commandInteractionData.options().isAbsent() && !commandInteractionData.options().get().isEmpty()) {
                List<ApplicationCommandInteractionOptionData> optionDataList = commandInteractionData.options().get();
                ApplicationCommandInteractionOptionData optionData = optionDataList.get(0);

                if (optionData.type() <= 2) { // Sub Command / Group
                    commandTree.add(optionData.name());

                    if (!optionData.options().isAbsent() && !optionData.options().get().isEmpty())
                        commandTree.add(optionData.options().get().get(0).name());
                }
            }
        }

        return commandTree;
    }

    protected final <T extends CommandReference> @NotNull Optional<T> getMatchingCommand(@NotNull Class<T> type, @NotNull ApplicationCommandInteractionData commandInteractionData) {
        return this.getDiscordBot()
            .getCommandRegistrar()
            .getLoadedCommands()
            .stream()
            .filter(commandEntry -> type.isAssignableFrom(commandEntry.getKey()))
            .map(Map.Entry::getValue)
            .map(type::cast)
            .filter(command -> command.doesMatch(this.getCommandTree(commandInteractionData)))
            .findFirst();
    }

    protected final @NotNull ConcurrentList<ApplicationCommandInteractionOptionData> getCommandOptionData(@NotNull ApplicationCommandInteractionData interactionOptionData) {
        ConcurrentList<ApplicationCommandInteractionOptionData> optionData = Concurrent.newList(interactionOptionData.options().toOptional().orElse(Concurrent.newUnmodifiableList()));
        ConcurrentList<String> commandTree = this.getCommandTree(interactionOptionData);
        commandTree.removeFirst();

        while (ListUtil.notEmpty(commandTree)) {
            for (ApplicationCommandInteractionOptionData option : optionData) {
                if (option.name().equals(commandTree.get(0))) {
                    commandTree.removeFirst();
                    optionData = Concurrent.newList(option.options().toOptional().orElseThrow());
                }
            }
        }

        return optionData;
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
        return channel.flatMap(chl -> chl.getEffectivePermissions(userId)).map(permissionSet -> this.getPermissionMap(permissionSet, permissions)).blockOptional().orElse(Concurrent.newLinkedMap());
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

    public final @NotNull Mono<Message> handleReactions(@NotNull Response response, @NotNull Message message) {
        return Mono.just(message)
            .checkpoint("DiscordReference#handleReactions Processing")
            .flatMap(msg -> {
                // Update Reactions
                ConcurrentList<Emoji> newReactions = response.getHistoryHandler().getCurrentPage().getReactions();

                // Current Reactions
                ConcurrentList<Emoji> currentReactions = message.getReactions()
                    .stream()
                    .filter(Reaction::selfReacted)
                    .map(Reaction::getEmoji)
                    .map(Emoji::of)
                    .collect(Concurrent.toList());

                Mono<Void> mono = Mono.empty();

                // Remove Existing Reactions
                if (currentReactions.stream().anyMatch(messageEmoji -> !newReactions.contains(messageEmoji)))
                    mono = message.removeAllReactions();

                return mono.then(Mono.when(
                    newReactions.stream()
                        .map(emoji -> message.addReaction(emoji.getD4jReaction()))
                        .collect(Concurrent.toList())
                ));
            })
            .thenReturn(message);
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
