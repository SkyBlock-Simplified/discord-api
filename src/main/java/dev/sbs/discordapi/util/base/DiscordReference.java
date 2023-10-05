package dev.sbs.discordapi.util.base;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.data.model.discord.emojis.EmojiModel;
import dev.sbs.api.data.model.discord.guild_data.guilds.GuildModel;
import dev.sbs.api.data.model.discord.users.UserModel;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.collection.concurrent.linked.ConcurrentLinkedMap;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.Command;
import dev.sbs.discordapi.command.UserPermission;
import dev.sbs.discordapi.command.data.CommandData;
import dev.sbs.discordapi.command.data.CommandId;
import dev.sbs.discordapi.command.relationship.Relationship;
import dev.sbs.discordapi.command.relationship.TopLevelRelationship;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.object.command.ApplicationCommandOption;
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
import java.util.UUID;
import java.util.function.Function;

public abstract class DiscordReference {

    protected abstract @NotNull DiscordBot getDiscordBot();

    public static @NotNull String capitalizeEnum(Enum<?> value) {
        return capitalizeFully(value.name());
    }

    public static @NotNull String capitalizeFully(String value) {
        return StringUtil.capitalizeFully(StringUtil.defaultIfEmpty(value, "").replace("_", " "));
    }

    // --- Command Searching ---
    public final boolean doesCommandMatch(@NotNull Relationship relationship, @NotNull Relationship compare) {
        return this.doesCommandMatch(relationship, compare.getName());
    }

    public final boolean doesCommandMatch(@NotNull Relationship relationship, @NotNull String argument) {
        return relationship.getName().equalsIgnoreCase(argument);
    }

    public static @NotNull <T extends Annotation> Optional<T> getAnnotation(@NotNull Class<T> tClass, @NotNull Class<? extends CommandData> command) {
        return command.isAnnotationPresent(tClass) ? Optional.of(command.getAnnotation(tClass)) : Optional.empty();
    }

    public static @NotNull Optional<CommandId> getCommandAnnotation(@NotNull Class<? extends CommandData> command) {
        return getAnnotation(CommandId.class, command);
    }

    public final @NotNull String getCommandPath(@NotNull Command command) {
        return StringUtil.join(this.getCommandPathList(command), " ");
    }

    public final @NotNull ConcurrentList<String> getCommandPathList(@NotNull Command command) {
        ConcurrentList<String> path = command.getParentCommandNames();

        // Get Root Command Prefix
        String rootCommand = this.getDiscordBot()
            .getRootCommandRelationship()
            .getName();

        // Remove Root For Slash
        if (ListUtil.notEmpty(path)) {
            if (StringUtil.isNotEmpty(rootCommand)) {
                if (path.get(0).equals(rootCommand))
                    path.remove(0);
            }
        }

        // Add Group
        command.getGroup().ifPresent(commandGroup -> path.add(commandGroup.getKey()));

        // Add Command Name
        path.add(command.getConfig().getName());

        return path;
    }

    public final @NotNull Optional<GuildModel> getGuild(@NotNull Snowflake guildId) {
        return SimplifiedApi.getRepositoryOf(GuildModel.class).findFirst(GuildModel::getGuildId, guildId.asLong());
    }

    public final @NotNull ConcurrentList<Relationship> getCompactedRelationships() {
        Relationship.Root rootRelationship = this.getDiscordBot().getRootCommandRelationship();
        ConcurrentList<Relationship> relationships = Concurrent.newList(rootRelationship);
        relationships.addAll(rootRelationship.getSubCommands());

        rootRelationship.getSubCommands()
            .stream()
            .filter(TopLevelRelationship.class::isInstance)
            .map(TopLevelRelationship.class::cast)
            .forEach(subCommandRelationship -> relationships.addAll(subCommandRelationship.getSubCommands()));

        return relationships;
    }

    private @NotNull String getCommandName(@NotNull ApplicationCommandInteractionOptionData commandOptionData) {
        if (commandOptionData.type() == ApplicationCommandOption.Type.SUB_COMMAND_GROUP.getValue())
            return this.getCommandName(commandOptionData.options().toOptional().orElse(Concurrent.newList()).get(0));

        return commandOptionData.name();
    }

    public static @NotNull Optional<Emoji> getEmoji(@NotNull String key) {
        return SimplifiedApi.getRepositoryOf(EmojiModel.class).findFirst(EmojiModel::getKey, key).flatMap(Emoji::of);
    }

    public static @NotNull String getEmojiAsFormat(@NotNull String key) {
        return getEmojiAsFormat(key, "");
    }

    public static @NotNull String getEmojiAsFormat(@NotNull String key, @NotNull String defaultValue) {
        return getEmoji(key).map(Emoji::asFormat).orElse(defaultValue);
    }

    public final @NotNull Optional<Relationship.Command> getDeepestCommand(@NotNull ApplicationCommandInteractionData commandInteractionData) {
        ConcurrentList<String> commandTree = Concurrent.newList(commandInteractionData.name().toOptional().orElseThrow()); // Should never be NULL

        if (!commandInteractionData.type().isAbsent()) {
            List<ApplicationCommandInteractionOptionData> optionDataList = commandInteractionData.options().toOptional().orElse(Concurrent.newList());

            if (ListUtil.notEmpty(optionDataList)) {
                ApplicationCommandInteractionOptionData firstOptionData = optionDataList.get(0);

                if (firstOptionData.type() <= 2) // Sub Command / Group
                    commandTree.add(this.getCommandName(firstOptionData));
            }
        }

        return this.getDeepestRelationship(commandTree, 0, this.getDiscordBot().getRootCommandRelationship(), true);
    }

    public final @NotNull Optional<Relationship.Command> getDeepestCommand(@NotNull String[] arguments) {
        return this.getDeepestCommand(arguments, 0);
    }

    public final @NotNull Optional<Relationship.Command> getDeepestCommand(@NotNull String[] arguments, int index) {
        return this.getDeepestRelationship(Concurrent.newList(arguments), index, this.getDiscordBot().getRootCommandRelationship(), false);
    }

    public final ConcurrentList<ApplicationCommandInteractionOptionData> getDeepestOptionData(Relationship.Command relationship, ApplicationCommandInteractionData interactionOptionData) {
        ConcurrentList<ApplicationCommandInteractionOptionData> optionData = Concurrent.newList(interactionOptionData.options().toOptional().orElseThrow());
        ConcurrentList<String> commandPathList = this.getCommandPathList(relationship.getInstance());
        commandPathList.remove(0); // Remove Root Command

        // Traverse Sub-Commands
        while (ListUtil.notEmpty(commandPathList)) {
            for (ApplicationCommandInteractionOptionData option : optionData) {
                if (option.name().equals(commandPathList.get(0))) {
                    commandPathList.remove(0);
                    optionData = Concurrent.newList(option.options().toOptional().orElseThrow());
                }
            }
        }

        return optionData;
    }

    private @NotNull Optional<Relationship.Command> getDeepestRelationship(@NotNull ConcurrentList<String> arguments, int index, @NotNull Relationship.Root rootRelationship, boolean slashCommands) {
        Relationship deepestRelationship = null;

        if (index < arguments.size()) {
            // Handle Prefix Command
            if (this.doesCommandMatch(rootRelationship, arguments.get(index)))
                index++;
            else if (!slashCommands)
                return Optional.empty();

            for (Relationship relationship : rootRelationship.getSubCommands()) {
                if (this.doesCommandMatch(relationship, arguments.get(index))) {
                    deepestRelationship = relationship;
                    index++;

                    if (relationship instanceof Relationship.Parent parentRelationship) {
                        for (Relationship.Command commandRelationship : parentRelationship.getSubCommands()) {
                            if (this.doesCommandMatch(commandRelationship, arguments.get(index))) {
                                deepestRelationship = commandRelationship;
                                break;
                            }
                        }
                    }

                    break;
                }
            }
        }

        return Optional.ofNullable(deepestRelationship instanceof Relationship.Command ? (Relationship.Command) deepestRelationship : null);
    }

    // --- Permissions ---
    public final @NotNull ConcurrentLinkedMap<Permission, Boolean> getGuildPermissionMap(@NotNull Snowflake userId, @NotNull Optional<Guild> guild, @NotNull Permission... permissions) {
        return this.getGuildPermissionMap(userId, guild, Arrays.asList(permissions));
    }

    public final @NotNull ConcurrentLinkedMap<Permission, Boolean> getGuildPermissionMap(@NotNull Snowflake userId, @NotNull Optional<Guild> optionalGuild, @NotNull Iterable<Permission> permissions) {
        return optionalGuild.map(guild -> guild.getMemberById(userId))
            .orElse(Mono.empty())
            .flatMap(Member::getBasePermissions)
            .map(permissionSet -> this.getPermissionMap(permissionSet, permissions))
            .blockOptional()
            .orElse(Concurrent.newLinkedMap());
    }

    public final @NotNull ConcurrentLinkedMap<Permission, Boolean> getChannelPermissionMap(@NotNull Snowflake userId, @NotNull Mono<GuildChannel> channel, @NotNull Permission... permissions) {
        return this.getChannelPermissionMap(userId, channel, Arrays.asList(permissions));
    }

    public final @NotNull ConcurrentLinkedMap<Permission, Boolean> getChannelPermissionMap(@NotNull Snowflake userId, @NotNull Mono<GuildChannel> channel, @NotNull Iterable<Permission> permissions) {
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
    public final boolean hasChannelPermissions(@NotNull Snowflake userId, @NotNull Mono<GuildChannel> channel, @NotNull Permission... permissions) {
        return this.hasChannelPermissions(userId, channel, Concurrent.newList(permissions));
    }

    public final boolean hasChannelPermissions(@NotNull Snowflake userId, @NotNull Mono<GuildChannel> channel, @NotNull Iterable<Permission> permissions) {
        return this.getChannelPermissionMap(userId, channel, permissions).stream().allMatch(Map.Entry::getValue);
    }

    // Guild Permissions
    public final boolean hasGuildPermissions(@NotNull Snowflake userId, @NotNull Optional<Guild> optionalGuild, @NotNull Permission... permissions) {
        return this.hasGuildPermissions(userId, optionalGuild, Arrays.asList(permissions));
    }

    public final boolean hasGuildPermissions(@NotNull Snowflake userId, @NotNull Optional<Guild> optionalGuild, @NotNull Iterable<Permission> permissions) {
        return this.getGuildPermissionMap(userId, optionalGuild, permissions).stream().allMatch(Map.Entry::getValue);
    }

    // User Permissions
    public final boolean doesUserHavePermissions(@NotNull Snowflake userId, @NotNull Optional<Guild> optionalGuild, @NotNull UserPermission... userPermissions) {
        return this.doesUserHavePermissions(userId, optionalGuild, Arrays.asList(userPermissions));
    }

    public final boolean doesUserHavePermissions(@NotNull Snowflake userId, @NotNull Optional<Guild> optionalGuild, @NotNull Iterable<UserPermission> userPermissions) {
        ConcurrentMap<UserPermission, Boolean> permissionMap = Concurrent.newMap();
        userPermissions.forEach(userPermission -> permissionMap.put(userPermission, this.doesUserHaveExactPermission(userId, optionalGuild, userPermission)));

        // Handle Included Permissions
        for (UserPermission aPermission : UserPermission.values()) {
            for (UserPermission userPermission : userPermissions) {
                if (aPermission.getIncludes().contains(userPermission))
                    permissionMap.put(userPermission, this.doesUserHaveExactPermission(userId, optionalGuild, aPermission));
            }
        }

        return permissionMap.stream().allMatch(Map.Entry::getValue); // All True
    }

    public final boolean doesUserHaveExactPermission(@NotNull Snowflake userId, @NotNull Optional<Guild> optionalGuild, @NotNull UserPermission userPermission) {
        return switch (userPermission) {
            case DEVELOPER -> this.isDeveloper(userId);
            case GUILD_OWNER -> this.isGuildOwner(userId, optionalGuild);
            case GUILD_ADMIN -> this.doesUserHaveGuildModelPermission(userId, optionalGuild, GuildModel::getAdminRoles) ||
                this.hasGuildPermissions(userId, optionalGuild, Permission.ADMINISTRATOR);
            case GUILD_MANAGER -> this.doesUserHaveGuildModelPermission(userId, optionalGuild, GuildModel::getManagerRoles);
            case GUILD_MOD -> this.doesUserHaveGuildModelPermission(userId, optionalGuild, GuildModel::getModRoles);
            case GUILD_HELPER -> this.doesUserHaveGuildModelPermission(userId, optionalGuild, GuildModel::getHelperRoles);
            case MAIN_SERVER -> optionalGuild.map(guild -> guild.getId().equals(this.getDiscordBot().getMainGuild().getId())).orElse(false);
            case EVERYONE -> true; // Default
        };
    }

    private boolean doesUserHaveGuildModelPermission(@NotNull Snowflake userId, @NotNull Optional<Guild> optionalGuild, @NotNull Function<GuildModel, List<Long>> guildRoleFunction) {
        return optionalGuild.flatMap(guild -> guild.getMemberById(userId)
                .map(member -> member.getRoleIds()
                    .stream()
                    .map(Snowflake::asLong)
                    .anyMatch(roleId -> SimplifiedApi.getRepositoryOf(GuildModel.class)
                        .findFirst(GuildModel::getGuildId, guild.getId().asLong())
                        .map(guildRoleFunction)
                        .orElse(Concurrent.newList())
                        .contains(roleId)
                    )
                )
                .blockOptional()
            )
            .orElse(false);
    }

    // --- Owner Permissions ---
    public final boolean isDeveloper(@NotNull Snowflake userId) {
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

    public final boolean isGuildOwner(@NotNull Snowflake userId, @Nullable Guild guild) {
        return this.isGuildOwner(userId, Optional.ofNullable(guild));
    }

    public final boolean isGuildOwner(@NotNull Snowflake userId, @NotNull Optional<Guild> optionalGuild) {
        return optionalGuild.map(guild -> guild.getOwnerId().equals(userId)).orElse(false);
    }

    public final boolean isUserVerified(@NotNull UUID uniqueId) {
        return SimplifiedApi.getRepositoryOf(UserModel.class).matchFirst(userModel -> userModel.getMojangUniqueIds().contains(uniqueId)).isPresent();
    }

}
