package dev.sbs.discordapi.command.reference;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentSet;
import discord4j.rest.util.Permission;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface CommandReference {

    boolean doesMatch(@NotNull ConcurrentList<String> commandTree);

    default @NotNull ConcurrentSet<Permission> getDefaultPermissions() {
        return Concurrent.newUnmodifiableSet();
    }

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

    @NotNull Type getType();

    default boolean isAvailableInPrivateChannels() {
        return true;
    }

    default boolean isEnabled() {
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

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    enum Type {

        UNKNOWN(-1),
        CHAT_INPUT(1),
        USER(2),
        MESSAGE(3);

        /**
         * The underlying value as represented by Discord.
         */
        @Getter
        private final int value;

        /**
         * Gets the type of application command. It is guaranteed that invoking {@link #getValue()} from the
         * returned enum will equal ({@code ==}) the supplied {@code value}.
         *
         * @param value The underlying value as represented by Discord.
         * @return The type of command.
         */
        public static Type of(final int value) {
            return switch (value) {
                case 1 -> CHAT_INPUT;
                case 2 -> USER;
                case 3 -> MESSAGE;
                default -> UNKNOWN;
            };
        }

    }

}
