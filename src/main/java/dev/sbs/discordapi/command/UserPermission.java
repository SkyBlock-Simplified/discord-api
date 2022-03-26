package dev.sbs.discordapi.command;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import lombok.Getter;

public enum UserPermission {

    EVERYONE,
    MAIN_SERVER,
    GUILD_HELPER(true),
    GUILD_MOD(true, GUILD_HELPER),
    GUILD_MANAGER(true, GUILD_MOD, GUILD_MOD),
    GUILD_ADMIN(true, GUILD_MANAGER, GUILD_MOD, GUILD_HELPER),
    GUILD_OWNER(true, GUILD_ADMIN, GUILD_MANAGER, GUILD_MOD, GUILD_HELPER),
    DEVELOPER(GUILD_OWNER, GUILD_ADMIN, GUILD_MANAGER, GUILD_MOD, GUILD_HELPER);

    @Getter private final boolean guildPermissible;
    @Getter private final ConcurrentList<UserPermission> includes;

    UserPermission(UserPermission... includes) {
        this(false, includes);
    }

    UserPermission(boolean guildPermissible, UserPermission... includes) {
        this.guildPermissible = guildPermissible;
        this.includes = Concurrent.newList(includes);
    }

}
