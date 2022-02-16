package dev.sbs.discordapi.util;

import dev.sbs.api.util.concurrent.Concurrent;
import dev.sbs.api.util.concurrent.ConcurrentList;
import dev.sbs.api.util.concurrent.ConcurrentMap;
import dev.sbs.api.util.concurrent.linked.ConcurrentLinkedMap;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.api.util.mutable.MutableBoolean;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.Command;
import dev.sbs.discordapi.command.UserPermission;
import dev.sbs.discordapi.command.data.CommandData;
import dev.sbs.discordapi.command.data.CommandInfo;
import discord4j.common.util.Snowflake;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.discordjson.json.ApplicationCommandInteractionData;
import discord4j.discordjson.json.ApplicationCommandInteractionOptionData;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class DiscordObject {

    @Getter private final DiscordBot discordBot;
    @Getter private final DiscordLogger log;
    
    protected DiscordObject(DiscordBot discordBot) {
        this.discordBot = discordBot;
        this.log = new DiscordLogger(this.getDiscordBot(), this.getClass());
    }

    // --- Command Searching ---
    public final boolean doesCommandMatch(@NotNull CommandInfo commandInfo, @NotNull String argument) {
        return commandInfo.name().equalsIgnoreCase(argument) || Arrays.stream(commandInfo.aliases()).anyMatch(alias -> argument.matches(FormatUtil.format("(?i:{0})", alias)));
    }

    public final <T extends Annotation> Optional<T> getAnnotation(@NotNull Class<T> tClass, @NotNull Class<? extends CommandData> command) {
        return command.isAnnotationPresent(tClass) ? Optional.of(command.getAnnotation(tClass)) : Optional.empty();
    }

    public final Optional<CommandInfo> getCommandAnnotation(@NotNull Class<? extends CommandData> command) {
        return this.getAnnotation(CommandInfo.class, command);
    }

    public final Optional<ReactionEmoji> getCommandEmoji(@NotNull Class<? extends Command> command) {
        Optional<CommandInfo> opAnnoCommand = getCommandAnnotation(command);

        // Get Custom Emoji
        if (opAnnoCommand.isPresent()) {
            CommandInfo annoCommandInfo = opAnnoCommand.get();
            Long emojiId = annoCommandInfo.emojiId() <= 0 ? null : annoCommandInfo.emojiId();

            if (annoCommandInfo.emojiId() > -1)
                return Optional.of(ReactionEmoji.of(emojiId, annoCommandInfo.emojiName(), annoCommandInfo.emojiAnimated()));
        }

        return Optional.empty();
    }

    public final ConcurrentList<Command.RelationshipData> getCompactedRelationships() {
        Command.RootRelationship rootRelationship = this.getDiscordBot().getRootCommandRelationship();
        ConcurrentList<Command.RelationshipData> relationships = Concurrent.newList(rootRelationship);
        rootRelationship.getSubCommands().forEach(subCommandRelationship -> relationships.addAll(this.getCompactedRelationships(subCommandRelationship)));
        return relationships;
    }

    private ConcurrentList<Command.RelationshipData> getCompactedRelationships(Command.Relationship rootRelationship) {
        ConcurrentList<Command.RelationshipData> relationships = Concurrent.newList(rootRelationship);
        rootRelationship.getSubCommands().forEach(subCommandRelationship -> relationships.addAll(this.getCompactedRelationships(subCommandRelationship)));
        return relationships;
    }

    private String getCommandName(ApplicationCommandInteractionOptionData commandOptionData) {
        if (commandOptionData.type() == ApplicationCommandOption.Type.SUB_COMMAND_GROUP.getValue())
            return this.getCommandName(commandOptionData.options().toOptional().orElse(Concurrent.newList()).get(0));

        return commandOptionData.name();
    }

    public final Optional<Command.Relationship> getDeepestCommand(ApplicationCommandInteractionData commandInteractionData) {
        ConcurrentList<String> commandTree = Concurrent.newList(commandInteractionData.name().toOptional().orElseThrow()); // Should never be NULL

        if (!commandInteractionData.type().isAbsent()) {
            List<ApplicationCommandInteractionOptionData> optionDataList = commandInteractionData.options().toOptional().orElse(Concurrent.newList());

            if (ListUtil.notEmpty(optionDataList)) {
                ApplicationCommandInteractionOptionData firstOptionData = optionDataList.get(0);

                if (firstOptionData.type() <= 2)
                    commandTree.add(this.getCommandName(firstOptionData));
            }
        }

        return this.getDeepestRelationship(commandTree, 0, this.getDiscordBot().getRootCommandRelationship(), true);
    }

    public final Optional<Command.Relationship> getDeepestCommand(String[] arguments) {
        return this.getDeepestCommand(arguments, 0);
    }

    public final Optional<Command.Relationship> getDeepestCommand(String[] arguments, int index) {
        return this.getDeepestRelationship(Concurrent.newList(arguments), index, this.getDiscordBot().getRootCommandRelationship(), false);
    }

    private Optional<Command.Relationship> getDeepestRelationship(ConcurrentList<String> arguments, int index, Command.RootRelationship rootRelationship, boolean slashCommands) {
        Command.Relationship deepestRelationship = null;
        Optional<CommandInfo> optionalPrefixCommandInfo = this.getCommandAnnotation(rootRelationship.getCommandClass());

        if (index < arguments.size()) {
            // Handle Prefix Command
            if (optionalPrefixCommandInfo.isPresent()) {
                if (this.doesCommandMatch(optionalPrefixCommandInfo.get(), arguments.get(index)))
                    index++;
                else if (!slashCommands)
                    return Optional.empty();
            }

            for (Command.Relationship relationship : rootRelationship.getSubCommands()) {
                if (this.doesCommandMatch(relationship.getCommandInfo(), arguments.get(index))) {
                    deepestRelationship = relationship;
                    index++;

                    for (Command.Relationship subRelationship : relationship.getSubCommands()) {
                        if (this.doesCommandMatch(subRelationship.getCommandInfo(), arguments.get(index))) {
                            deepestRelationship = subRelationship;
                            break;
                        }
                    }

                    break;
                }
            }
        }

        return Optional.ofNullable(deepestRelationship);
    }

    public final ConcurrentList<Command.RelationshipData> getParentCommandList(Class<? extends Command> commandClass) {
        ConcurrentList<Command.RelationshipData> parentCommands = Concurrent.newList();
        ConcurrentList<Command.RelationshipData> compactedRelationships = this.getCompactedRelationships();

        // Handle Parent Commands
        while (true) {
            Optional<Command.RelationshipData> optionalRelationshipData = Optional.empty();

            // Find Matching Relationship
            for (Command.RelationshipData relationship : compactedRelationships) {
                if (relationship.getCommandClass().equals(commandClass)) {
                    optionalRelationshipData = Optional.of(relationship);
                    break;
                }
            }

            // Find Matching Parent Relationship
            if (optionalRelationshipData.isPresent()) {
                Command.RelationshipData relationshipData = optionalRelationshipData.get();

                if (relationshipData.getOptionalCommandInfo().isPresent()) {
                    CommandInfo commandInfo = relationshipData.getOptionalCommandInfo().get();

                    if (commandInfo.parent().equals(Command.class))
                        break;

                    for (Command.RelationshipData relationship : compactedRelationships) {
                        if (relationship.getCommandClass().equals(commandInfo.parent())) {
                            parentCommands.add(relationship);
                            commandClass = commandInfo.parent();
                            break;
                        }
                    }
                }
            }
        }

        // Handle Prefix Command
        if (this.getDiscordBot().getRootCommandRelationship().getOptionalCommandInfo().isPresent())
            parentCommands.add(this.getDiscordBot().getRootCommandRelationship());

        return Concurrent.newUnmodifiableList(parentCommands.inverse());
    }

    // --- Permissions ---
    public final ConcurrentLinkedMap<Permission, Boolean> getGuildPermissionMap(Snowflake userId, Mono<Guild> guild, Permission... permissions) {
        return this.getGuildPermissionMap(userId, guild, Arrays.asList(permissions));
    }

    public final ConcurrentLinkedMap<Permission, Boolean> getGuildPermissionMap(Snowflake userId, Mono<Guild> guild, Iterable<Permission> permissions) {
        return guild.flatMap(gld -> gld.getMemberById(userId)).flatMap(Member::getBasePermissions).map(permissionSet -> this.getPermissionMap(permissionSet, permissions)).blockOptional().orElse(Concurrent.newLinkedMap());
    }

    public final ConcurrentLinkedMap<Permission, Boolean> getChannelPermissionMap(Snowflake userId, Mono<GuildChannel> channel, Permission... permissions) {
        return this.getChannelPermissionMap(userId, channel, Arrays.asList(permissions));
    }

    public final ConcurrentLinkedMap<Permission, Boolean> getChannelPermissionMap(Snowflake userId, Mono<GuildChannel> channel, Iterable<Permission> permissions) {
        return channel.flatMap(chl -> chl.getEffectivePermissions(userId)).map(permissionSet -> this.getPermissionMap(permissionSet, permissions)).blockOptional().orElse(Concurrent.newLinkedMap());
    }

    private ConcurrentLinkedMap<Permission, Boolean> getPermissionMap(PermissionSet permissionSet, Iterable<Permission> permissions) {
        // Set Default Permissions
        ConcurrentLinkedMap<Permission, Boolean> permissionMap = Concurrent.newLinkedMap();
        permissions.forEach(permission -> permissionMap.put(permission, false));

        // Check Permissions
        for (Permission permission : permissions)
            permissionMap.put(permission, permissionSet.contains(permission));

        return permissionMap;
    }

    // Channel Permissions
    public final boolean hasChannelPermissions(Snowflake userId, Mono<GuildChannel> channel, Permission... permissions) {
        return this.hasChannelPermissions(userId, channel, Concurrent.newList(permissions));
    }

    public final boolean hasChannelPermissions(Snowflake userId, Mono<GuildChannel> channel, Iterable<Permission> permissions) {
        return this.getChannelPermissionMap(userId, channel, permissions).stream().allMatch(Map.Entry::getValue);
    }

    // Guild Permissions
    public final boolean hasGuildPermissions(Snowflake userId, Mono<Guild> guild, Permission... permissions) {
        return this.hasGuildPermissions(userId, guild, Arrays.asList(permissions));
    }

    public final boolean hasGuildPermissions(Snowflake userId, Mono<Guild> guild, Iterable<Permission> permissions) {
        return this.getGuildPermissionMap(userId, guild, permissions).stream().allMatch(Map.Entry::getValue);
    }

    // User Permissions
    public final boolean doesUserHavePermissions(Snowflake snowflake, Mono<Guild> guild, UserPermission... userPermissions) {
        return this.doesUserHavePermissions(snowflake, guild, Arrays.asList(userPermissions));
    }

    public final boolean doesUserHavePermissions(Snowflake snowflake, Mono<Guild> guild, Iterable<UserPermission> userPermissions) {
        // Set Default Permissions
        ConcurrentMap<UserPermission, Boolean> permissionMap = Concurrent.newMap();
        userPermissions.forEach(userPermission -> permissionMap.put(userPermission, false));

        guild.flatMap(gld -> gld.getMemberById(snowflake))
            .blockOptional()
            .ifPresent(member -> {
                for (UserPermission userPermission : userPermissions) {
                    switch (userPermission) {
                        case BOT_OWNER -> permissionMap.put(userPermission, this.isBotOwner(snowflake));
                        case GUILD_OWNER -> permissionMap.put(userPermission, this.isGuildOwner(snowflake, guild));
                        case MAIN_SERVER_ADMIN -> permissionMap.put(userPermission, this.hasGuildPermissions(snowflake, Mono.just(getDiscordBot().getMainGuild()), Permission.ADMINISTRATOR));
                        case MAIN_SERVER -> permissionMap.put(userPermission, guild.map(gld -> gld.equals(getDiscordBot().getMainGuild())).blockOptional().orElse(false));
                        case NONE -> permissionMap.put(userPermission, true); // Default
                        default -> permissionMap.put(userPermission, true); // Default
                    }
                }
            });

        return permissionMap.stream().allMatch(Map.Entry::getValue); // All True
    }

    // --- Owner Permissions ---
    public final boolean isBotOwner(Snowflake userId) {
        MutableBoolean isOwner = new MutableBoolean(false);

        this.getDiscordBot()
            .getClient()
            .getApplicationInfo()
            .cache()
            .blockOptional()
            .ifPresent(applicationInfoData -> applicationInfoData
                .team()
                .ifPresentOrElse(applicationTeamData -> applicationTeamData.members().forEach(applicationTeamMemberData -> {
                    if (applicationTeamMemberData.user().id().asLong() == userId.asLong())
                        isOwner.set(true);
                }), () -> {
                    if (applicationInfoData.owner().id().asLong() == userId.asLong())
                        isOwner.set(true);
                })
            );

        return isOwner.get();
    }

    public final boolean isGuildOwner(Snowflake userId, @NotNull Mono<Guild> guild) {
        return guild.map(gld -> gld.getOwnerId().equals(userId)).blockOptional().orElse(false);
    }

}
