package dev.sbs.discordapi.command.data;

import dev.sbs.api.util.collection.concurrent.ConcurrentSet;
import dev.sbs.discordapi.command.UserPermission;
import discord4j.rest.util.Permission;
import org.jetbrains.annotations.NotNull;

public interface CommandData {

    @NotNull ConcurrentSet<Permission> getPermissions();

    @NotNull ConcurrentSet<UserPermission> getUserPermissions();

}
