package dev.simplified.discordapi.command;

import dev.simplified.discordapi.command.exception.DisabledCommandException;
import dev.simplified.discordapi.context.scope.CommandContext;
import dev.simplified.discordapi.handler.DiscordConfig;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * Pluggable resolver deciding whether a {@link DiscordCommand} is currently enabled - the seam that lets a
 * bot toggle commands on and off at runtime while keeping the framework storage-agnostic.
 *
 * <p>
 * The framework defines this interface and ships {@link InMemoryCommandStateResolver} as a process-local
 * default. Consumers that need enabled-state to survive restarts wire a database-backed implementation through
 * {@link DiscordConfig.Builder#withCommandStateResolver}; the framework consults the interface only. Identify
 * a command by its serializable {@link DiscordCommand#getCommandKey() CommandKey}, never its name or numeric
 * id alone.
 *
 * <p>
 * The resolver is consulted for non-developers only - developers bypass the disabled gate - and a rejected
 * command surfaces to the user as a {@link DisabledCommandException}.
 *
 * @see CommandKey
 * @see InMemoryCommandStateResolver
 */
@FunctionalInterface
public interface CommandStateResolver {

    /**
     * Resolves whether the given command may run for this invocation.
     *
     * <p>
     * An empty {@link Mono} is treated as enabled, so a silent resolver never locks out a command.
     *
     * @param command the command being dispatched
     * @param context the invoking command context, exposing the user, guild, and channel
     * @return a mono emitting {@code true} if the command may proceed, {@code false} to reject it as disabled
     */
    @NotNull Mono<Boolean> isEnabled(@NotNull DiscordCommand<?> command, @NotNull CommandContext<?> context);

}
