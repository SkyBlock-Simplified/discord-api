package dev.sbs.discordapi.command;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import lombok.Getter;

public enum UserPermission {

    NONE,
    MAIN_SERVER,
    GUILD_HELPER,
    GUILD_MOD(GUILD_HELPER),
    GUILD_MANAGER(GUILD_MOD, GUILD_MOD),
    GUILD_ADMIN(GUILD_MANAGER, GUILD_MOD, GUILD_HELPER),
    GUILD_OWNER(GUILD_ADMIN, GUILD_MANAGER, GUILD_MOD, GUILD_HELPER),
    DEVELOPER(GUILD_OWNER, GUILD_ADMIN, GUILD_MANAGER, GUILD_MOD, GUILD_HELPER);

    @Getter private final ConcurrentList<UserPermission> includes;

    UserPermission(UserPermission... includes) {
        this.includes = Concurrent.newList(includes);
    }

}
