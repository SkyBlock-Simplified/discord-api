package dev.sbs.discordapi.command;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.command.CommandContext;
import dev.sbs.discordapi.util.exception.DiscordException;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

public abstract class ParentCommand extends Command {

    protected ParentCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected final @NotNull Mono<Void> process(@NotNull CommandContext<?> commandContext) throws DiscordException {
        return Mono.empty();
    }

}
