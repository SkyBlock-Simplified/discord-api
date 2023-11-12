package dev.sbs.discordapi.command.impl;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.unmodifiable.ConcurrentUnmodifiableList;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.CommandId;
import dev.sbs.discordapi.command.exception.DisabledCommandException;
import dev.sbs.discordapi.command.exception.parameter.InvalidParameterException;
import dev.sbs.discordapi.command.exception.parameter.MissingParameterException;
import dev.sbs.discordapi.command.exception.permission.BotPermissionException;
import dev.sbs.discordapi.command.exception.permission.UserPermissionException;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.reference.CommandReference;
import dev.sbs.discordapi.context.CommandContext;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.util.base.DiscordHelper;
import dev.sbs.discordapi.util.exception.DiscordException;
import discord4j.core.event.domain.Event;
import discord4j.core.object.entity.channel.GuildChannel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

public abstract class DiscordCommand<E extends Event, T extends CommandContext<E>> extends DiscordHelper implements CommandReference, Function<T, Mono<Void>> {

    protected static final ConcurrentUnmodifiableList<String> NO_EXAMPLES = Concurrent.newUnmodifiableList();
    protected static final ConcurrentList<String> helpArguments = Concurrent.newUnmodifiableList("help", "?");
    @Getter protected final @NotNull DiscordBot discordBot;
    @Getter protected final @NotNull UUID uniqueId;

    protected DiscordCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
        this.discordBot = discordBot;
        this.uniqueId = getCommandUniqueId(this.getClass())
            .map(CommandId::value)
            .map(StringUtil::toUUID)
            .orElseThrow(); // Will Never Throw
    }

    @Override
    public final long getId() {
        return this.getDiscordBot()
            .getCommandRegistrar()
            .getApiCommandId(this.getClass());
    }

    protected abstract @NotNull Mono<Void> process(@NotNull T commandContext) throws DiscordException;

    @Override
    public final @NotNull Mono<Void> apply(@NotNull T commandContext) {
        return commandContext.withEvent(event -> commandContext.withGuild(optionalGuild -> commandContext.withChannel(messageChannel -> commandContext
            .deferReply()
            .then(Mono.fromCallable(() -> {
                // Handle Developer Command
                if (this.isDeveloperOnly() && !this.isDeveloper(commandContext.getInteractUserId()))
                    throw SimplifiedException.of(UserPermissionException.class)
                        .withMessage("Only the bot developer can run this command!")
                        .build();

                // Handle Disabled Command
                if (!this.isEnabled() && !this.isDeveloper(commandContext.getInteractUserId()))
                    throw SimplifiedException.of(DisabledCommandException.class).withMessage("This command is currently disabled!").build();

                // Handle Bot Permissions
                if (!commandContext.isPrivateChannel()) {
                    // Handle Required Permissions
                    if (!this.hasChannelPermissions(this.getDiscordBot().getClientId(), commandContext.getChannel().ofType(GuildChannel.class), this.getRequiredPermissions()))
                        throw SimplifiedException.of(BotPermissionException.class)
                            .withMessage("The command '%s' lacks permissions required to run!", this.getName())
                            .addData("ID", this.getDiscordBot().getClientId())
                            .addData("PERMISSIONS", this.getRequiredPermissions())
                            .build();
                }

                // Validate Arguments
                for (Argument argument : commandContext.getArguments()) {
                    if (argument.getValue().isEmpty()) {
                        if (argument.getParameter().isRequired())
                            throw SimplifiedException.of(MissingParameterException.class)
                                .addData("ARGUMENT", argument)
                                .addData("MISSING", true)
                                .build();
                    } else {
                        if (!argument.getParameter().getType().isValid(argument.getValue()))
                            throw SimplifiedException.of(InvalidParameterException.class)
                                .addData("ARGUMENT", argument)
                                .addData("MISSING", false)
                                .build();

                        if (!argument.getParameter().isValid(argument.getValue(), commandContext))
                            throw SimplifiedException.of(InvalidParameterException.class)
                                .addData("ARGUMENT", argument)
                                .addData("MISSING", false)
                                .build();
                    }
                }

                // Process Command
                return this.process(commandContext);
            }))
            .flatMap(Function.identity())
            .onErrorResume(throwable -> this.getDiscordBot().handleException(ExceptionContext.of(this.getDiscordBot(), commandContext, throwable)))
        )));
    }

}
