package dev.sbs.discordapi.handler.exception;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.capability.ExceptionContext;
import dev.sbs.discordapi.util.DiscordReference;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * Abstract base for exception handlers that process errors occurring during
 * Discord event handling, command execution, and listener invocation.
 *
 * <p>
 * Subclasses define how exceptions are reported to users and logged for
 * developers. The handler receives an {@link ExceptionContext} containing
 * the originating event, the thrown exception, and contextual metadata.
 *
 * @see DiscordExceptionHandler
 * @see SentryExceptionHandler
 */
public abstract class ExceptionHandler extends DiscordReference {

    /**
     * Constructs a new {@code ExceptionHandler} with the given bot instance.
     *
     * @param discordBot the bot this handler belongs to
     */
    public ExceptionHandler(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /**
     * Handles the exception described by the given context.
     *
     * <p>
     * Implementations typically send an error response to the user, log
     * the exception to a developer channel, and optionally re-emit the
     * error as a reactive signal for uncaught exceptions.
     *
     * @param exceptionContext the context wrapping the exception and its originating event
     * @param <T> the downstream element type of the returned {@link Mono}
     * @return a mono that completes when the exception has been fully processed
     */
    public abstract <T> @NotNull Mono<T> handleException(@NotNull ExceptionContext<?> exceptionContext);

}
