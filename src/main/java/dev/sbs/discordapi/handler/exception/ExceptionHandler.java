package dev.sbs.discordapi.handler.exception;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.util.DiscordReference;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

public abstract class ExceptionHandler extends DiscordReference {

    public ExceptionHandler(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    public abstract <T> @NotNull Mono<T> handleException(@NotNull ExceptionContext<?> exceptionContext);

}
