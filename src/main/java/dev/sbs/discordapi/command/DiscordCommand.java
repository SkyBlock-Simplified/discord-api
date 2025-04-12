package dev.sbs.discordapi.command;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.unmodifiable.ConcurrentUnmodifiableList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.context.TypeContext;
import dev.sbs.discordapi.command.exception.CommandException;
import dev.sbs.discordapi.command.exception.DisabledCommandException;
import dev.sbs.discordapi.command.exception.permission.BotPermissionException;
import dev.sbs.discordapi.command.exception.permission.DeveloperPermissionException;
import dev.sbs.discordapi.context.deferrable.command.CommandContext;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.exception.DiscordException;
import dev.sbs.discordapi.util.DiscordReference;
import discord4j.core.object.entity.channel.GuildChannel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.function.Function;

@Getter
public abstract class DiscordCommand<C extends CommandContext<?>> extends DiscordReference implements Function<C, Mono<Void>> {

    protected static final ConcurrentUnmodifiableList<String> NO_EXAMPLES = Concurrent.newUnmodifiableList();
    protected final @NotNull DiscordBot discordBot;
    protected final @NotNull CommandStructure structure;

    protected DiscordCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
        this.discordBot = discordBot;
        this.structure = super.getAnnotation(CommandStructure.class, this.getClass())
            .orElseThrow(() -> new CommandException("Cannot instantiate a command with no structure."));
    }

    public final long getId() {
        return this.getDiscordBot()
            .getCommandHandler()
            .getApiCommandId(this.getClass());
    }

    public abstract @NotNull TypeContext getType();

    protected void handleAdditionalChecks(@NotNull C commandContext) { }

    public boolean isEnabled() {
        return true; // TODO: Reimplement
    }

    protected abstract @NotNull Mono<Void> process(@NotNull C commandContext) throws DiscordException;

    @Override
    public final @NotNull Mono<Void> apply(@NotNull C commandContext) {
        return commandContext.withEvent(event -> commandContext.withGuild(optionalGuild -> commandContext.withChannel(messageChannel -> commandContext
            .deferReply(commandContext.getCommand().getStructure().ephemeral())
            .then(Mono.defer(() -> {
                // Handle Developer Command
                if (this.getStructure().developerOnly() && !this.isDeveloper(commandContext.getInteractUserId()))
                    throw new DeveloperPermissionException();

                // Handle Disabled Command
                if (!this.isEnabled() && !this.isDeveloper(commandContext.getInteractUserId()))
                    throw new DisabledCommandException();

                // Handle Bot Permissions
                if (!commandContext.isPrivateChannel()) {
                    // Handle Required Permissions
                    if (!this.hasChannelPermissions(this.getDiscordBot().getClientId(), commandContext.getChannel().ofType(GuildChannel.class), this.getStructure().botPermissions()))
                        throw new BotPermissionException(commandContext, Concurrent.newUnmodifiableSet(this.getStructure().botPermissions()));
                }

                // Process Additional Checks
                this.handleAdditionalChecks(commandContext);

                // Process Command
                return this.process(commandContext);
            }))
            .onErrorResume(throwable -> this.getDiscordBot().getExceptionHandler().handleException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    commandContext,
                    throwable
                )
            ))
        )));
    }

}
