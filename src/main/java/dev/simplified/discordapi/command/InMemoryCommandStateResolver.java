package dev.simplified.discordapi.command;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentSet;
import dev.simplified.discordapi.context.scope.CommandContext;
import dev.simplified.discordapi.handler.DiscordConfig;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * Process-local default {@link CommandStateResolver}: tracks disabled commands in an in-memory
 * {@link CommandKey} set. State is lost on bot restart.
 *
 * <p>
 * Useful for the offline harness and bots whose disable toggles need not survive a reboot. Deployments that
 * need persistent enabled-state plug a durable {@link CommandStateResolver} through
 * {@link DiscordConfig.Builder#withCommandStateResolver}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InMemoryCommandStateResolver implements CommandStateResolver {

    private final @NotNull ConcurrentSet<CommandKey> disabled = Concurrent.newSet();

    /**
     * Constructs a fresh resolver with every command enabled.
     *
     * @return a new in-memory resolver
     */
    public static @NotNull InMemoryCommandStateResolver of() {
        return new InMemoryCommandStateResolver();
    }

    /**
     * Disables the given command.
     *
     * @param command the command to disable
     */
    public void disable(@NotNull DiscordCommand<?> command) {
        this.disable(command.getCommandKey());
    }

    /**
     * Disables the command with the given key.
     *
     * @param key the command identity to disable
     */
    public void disable(@NotNull CommandKey key) {
        this.disabled.add(key);
    }

    /**
     * Enables the given command.
     *
     * @param command the command to enable
     */
    public void enable(@NotNull DiscordCommand<?> command) {
        this.enable(command.getCommandKey());
    }

    /**
     * Enables the command with the given key.
     *
     * @param key the command identity to enable
     */
    public void enable(@NotNull CommandKey key) {
        this.disabled.remove(key);
    }

    /**
     * Returns whether the command with the given key is currently disabled.
     *
     * @param key the command identity to check
     * @return {@code true} if the command is disabled
     */
    public boolean isDisabled(@NotNull CommandKey key) {
        return this.disabled.contains(key);
    }

    /**
     * Returns an unmodifiable snapshot of the currently disabled command keys, for persisting on shutdown.
     *
     * @return the disabled command identities
     */
    public @NotNull ConcurrentSet<CommandKey> getDisabled() {
        return this.disabled.toUnmodifiable();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Mono<Boolean> isEnabled(@NotNull DiscordCommand<?> command, @NotNull CommandContext<?> context) {
        return Mono.just(!this.disabled.contains(command.getCommandKey()));
    }

}
