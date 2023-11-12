package dev.sbs.discordapi.command.reference;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentSet;
import discord4j.core.object.command.ApplicationCommand;
import discord4j.rest.util.Permission;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface CommandReference {

    boolean doesMatch(@NotNull ConcurrentList<String> commandTree);

    default @NotNull ConcurrentSet<Permission> getDefaultPermissions() {
        return Concurrent.newUnmodifiableSet();
    }

    @NotNull String getCommandPath();

    @NotNull String getDescription();

    long getGuildId();

    long getId();

    @NotNull String getName();

    /**
     * Immutable set of {@link Permission discord permissions} required for the bot to process this command.
     *
     * @return The required discord permissions.
     */
    default @NotNull ConcurrentSet<Permission> getRequiredPermissions() {
        return Concurrent.newUnmodifiableSet();
    }

    @NotNull ApplicationCommand.Type getType();

    default boolean isAvailableInPrivateChannels() {
        return true;
    }

    default boolean isDeveloperOnly() {
        return false;
    }

    default boolean isInheritingPermissions() {
        return true;
    }

    default boolean isNsfw() {
        return false;
    }

    @NotNull UUID getUniqueId();

}
