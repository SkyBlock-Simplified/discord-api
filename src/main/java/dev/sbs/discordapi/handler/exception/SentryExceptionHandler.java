package dev.sbs.discordapi.handler.exception;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * Sentry-backed exception handler that forwards exceptions to the
 * <a href="https://sentry.io/">Sentry</a> error tracking service.
 *
 * <p>
 * This implementation is currently a stub and always returns an empty
 * {@link Mono}. Future versions will capture exceptions, attach contextual
 * breadcrumbs, and submit them to a configured Sentry project.
 */
public class SentryExceptionHandler extends ExceptionHandler {

    /**
     * Constructs a new {@code SentryExceptionHandler} with the given bot instance.
     *
     * @param discordBot the bot this handler belongs to
     */
    public SentryExceptionHandler(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull <T> Mono<T> handleException(@NotNull ExceptionContext<?> exceptionContext) {
        return Mono.empty(); // TODO: Sentry Service https://sentry.io/welcome/
    }

}
