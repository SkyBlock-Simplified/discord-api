package dev.sbs.discordapi.command.reference;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentSet;
import dev.sbs.discordapi.context.deferrable.command.CommandContext;
import discord4j.rest.util.Permission;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

public interface CommandReference<C extends CommandContext<?>> extends Function<C, Mono<Void>> {

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

    default boolean isEphemeral() {
        return false;
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

    @Getter
    @RequiredArgsConstructor
    enum Type {

        UNKNOWN(-1),
        CHAT_INPUT(1),
        USER(2),
        MESSAGE(3);

        /**
         * The underlying value as represented by Discord.
         */
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
