package dev.sbs.discordapi.command;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.collection.concurrent.unmodifiable.ConcurrentUnmodifiableList;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.exception.CommandException;
import dev.sbs.discordapi.command.exception.DisabledCommandException;
import dev.sbs.discordapi.command.exception.input.ParameterException;
import dev.sbs.discordapi.command.exception.permission.BotPermissionException;
import dev.sbs.discordapi.command.exception.permission.DeveloperPermissionException;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.context.deferrable.command.CommandContext;
import dev.sbs.discordapi.context.deferrable.command.MessageCommandContext;
import dev.sbs.discordapi.context.deferrable.command.SlashCommandContext;
import dev.sbs.discordapi.context.deferrable.command.UserCommandContext;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.exception.DiscordException;
import dev.sbs.discordapi.util.DiscordReference;
import discord4j.core.object.entity.channel.GuildChannel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

@Getter
public abstract class DiscordCommand<C extends CommandContext<?>> extends DiscordReference implements Function<C, Mono<Void>> {

    protected static final ConcurrentUnmodifiableList<String> NO_EXAMPLES = Concurrent.newUnmodifiableList();
    protected final @NotNull Structure structure;
    protected final @NotNull Class<C> contextType;

    protected DiscordCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
        this.structure = super.getAnnotation(Structure.class, this.getClass())
            .orElseThrow(() -> new CommandException("Cannot instantiate a command with no structure."));
        this.contextType = Reflection.getSuperClass(this);
    }

    public final long getId() {
        return this.getDiscordBot()
            .getCommandHandler()
            .getCommandId(this.getClass());
    }

    public final @NotNull Optional<Parameter> getParameter(int index) {
        ConcurrentList<Parameter> parameters = this.getParameters();
        index = Math.max(0, index);
        return Optional.ofNullable(index < parameters.size() ? parameters.get(index) : null);
    }

    /**
     * Implement this for {@link SlashCommandContext Slash Commands}.
     *
     * <ul>
     *     <li>This is ignored for {@link UserCommandContext User Commands} and {@link MessageCommandContext Message Commands}.</li>
     * </ul>
     */
    public @NotNull ConcurrentUnmodifiableList<Parameter> getParameters() {
        return Concurrent.newUnmodifiableList();
    }

    private void handleParameterChecks(@NotNull SlashCommandContext slashCommandContext) {
        // Validate Arguments
        for (Parameter parameter : this.getParameters()) {
            Optional<Argument> argument = slashCommandContext.getArgument(parameter.getName());

            if (argument.isEmpty())
                continue;

            String value = argument.map(Argument::asString).get();

            if (!parameter.getType().isValid(value))
                throw new ParameterException(parameter, value, "Value '%s' does not match parameter type for '%s'.", value, parameter.getType().name());

            if (!parameter.isValid(value, slashCommandContext))
                throw new ParameterException(parameter, value, "Value '%s' does not validate against parameter '%s'.", value, parameter.getName());
        }
    }

    public boolean isEnabled() {
        return true; // TODO: Reimplement
    }

    protected abstract @NotNull Mono<Void> process(@NotNull C context) throws DiscordException;

    @Override
    public final @NotNull Mono<Void> apply(@NotNull C context) {
        return context.withEvent(event -> context.withChannel(messageChannel -> context
            .deferReply(this.getStructure().ephemeral())
            .then(Mono.defer(() -> {
                // Handle Developer Command
                if (this.getStructure().developerOnly() && !this.isDeveloper(context.getInteractUserId()))
                    throw new DeveloperPermissionException();

                // Handle Disabled Command
                if (!this.isEnabled() && !this.isDeveloper(context.getInteractUserId()))
                    throw new DisabledCommandException();

                // Handle Bot Permissions
                if (!context.isPrivateChannel()) {
                    // Handle Required Permissions
                    if (!this.hasChannelPermissions(this.getDiscordBot().getClientId(), context.getChannel().ofType(GuildChannel.class), this.getStructure().botPermissions()))
                        throw new BotPermissionException(context, Concurrent.newUnmodifiableSet(this.getStructure().botPermissions()));
                }

                // Process Parameter Checks
                if (context instanceof SlashCommandContext slashCommandContext)
                    this.handleParameterChecks(slashCommandContext);

                // Process Command
                return this.process(context);
            }))
            .onErrorResume(throwable -> this.getDiscordBot().getExceptionHandler().handleException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    context,
                    throwable
                )
            ))
        ));
    }

    @Getter
    @RequiredArgsConstructor
    public enum Access {

        UNKNOWN(-1),
        /**
         * Interaction can be used within servers
         */
        GUILD(0),
        /**
         * Interaction can be used within DMs with the app's bot user
         */
        DIRECT_MESSAGE(1),
        /**
         * Interaction can be used within Group DMs and DMs other than the app's bot user
         */
        PRIVATE_CHANNEL(2);

        /**
         * The underlying value as represented by Discord.
         */
        private final int value;

        public static @NotNull Access of(final int value) {
            return switch (value) {
                case 0 -> GUILD;
                case 1 -> DIRECT_MESSAGE;
                case 2 -> PRIVATE_CHANNEL;
                default -> UNKNOWN;
            };
        }

        public static @NotNull Integer[] intValues(@NotNull Access[] contexts) {
            return Arrays.stream(contexts).map(Access::getValue).toArray(Integer[]::new);
        }

    }

    @Getter
    @RequiredArgsConstructor
    public enum Install {

        UNKNOWN(-1),
        /**
         * Installable to servers
         */
        GUILD(0),
        /**
         * Installable to users
         */
        USER(1);

        /**
         * The underlying value as represented by Discord.
         */
        private final int value;

        public static @NotNull Install of(final int value) {
            return switch (value) {
                case 0 -> GUILD;
                case 1 -> USER;
                default -> UNKNOWN;
            };
        }

        public static @NotNull Integer[] intValues(@NotNull Install[] contexts) {
            return Arrays.stream(contexts).map(Install::getValue).toArray(Integer[]::new);
        }

    }

    @Getter
    @RequiredArgsConstructor
    public enum Type {

        UNKNOWN(-1),
        /**
         * Slash commands; a text-based command that shows up when a user types /
         */
        CHAT_INPUT(1),
        /**
         * A UI-based command that shows up when you right click or tap on a user
         */
        USER(2),
        /**
         * A UI-based command that shows up when you right click or tap on a message
         */
        MESSAGE(3),
        /**
         * A UI-based command that represents the primary way to invoke an app's Activity
         */
        PRIMARY_ENTRY_POINT(4);

        /**
         * The underlying value as represented by Discord.
         */
        private final int value;

        public static @NotNull Type of(final int value) {
            return switch (value) {
                case 1 -> CHAT_INPUT;
                case 2 -> USER;
                case 3 -> MESSAGE;
                case 4 -> PRIMARY_ENTRY_POINT;
                default -> UNKNOWN;
            };
        }

    }

    /*public Embed createHelpEmbed() {
        String commandPath = this.getCommandPath();
        ConcurrentList<Parameter> parameters = this.getParameters();

        Embed.Builder builder = Embed.builder()
            .withAuthor(
                Author.builder()
                    .withName("Help")
                    .withIconUrl(getEmoji("STATUS_INFO").map(Emoji::getUrl))
                    .build()
            )
            .withTitle("Command :: %s", this.getStructure().name())
            .withDescription(this.getStructure().description())
            .withFooter(
                Footer.builder()
                    .withTimestamp(Instant.now())
                    .build()
            )
            .withColor(Color.DARK_GRAY);

        if (parameters.notEmpty()) {
            builder.withField(
                "Usage",
                String.format(
                    """
                        <> - Required Parameters
                        [] - Optional Parameters

                        %s %s""",
                    commandPath,
                    StringUtil.join(
                        parameters.stream()
                            .map(parameter -> parameter.isRequired() ? String.format("<%s>", parameter.getName()) : String.format("[%s]", parameter.getName()))
                            .collect(Concurrent.toList()),
                        " "
                    )
                )
            );
        }*/

        /*if (this.getExampleArguments().notEmpty()) {
            builder.withField(
                "Examples",
                StringUtil.join(
                    this.getExampleArguments()
                        .stream()
                        .map(example -> String.format("%s %s", commandPath, example))
                        .collect(Concurrent.toList()),
                    "\n"
                )
            );
        }*/

        /*return builder.build();
    }*/

}
