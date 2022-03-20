package dev.sbs.discordapi.util.base;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.client.hypixel.exception.HypixelApiException;
import dev.sbs.api.client.sbs.exception.SbsApiException;
import dev.sbs.api.data.model.discord.emojis.EmojiModel;
import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.linked.ConcurrentLinkedMap;
import dev.sbs.api.util.data.tuple.Pair;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.command.data.Argument;
import dev.sbs.discordapi.command.data.Parameter;
import dev.sbs.discordapi.command.exception.CommandException;
import dev.sbs.discordapi.command.exception.DisabledCommandException;
import dev.sbs.discordapi.command.exception.HelpCommandException;
import dev.sbs.discordapi.command.exception.parameter.ParameterException;
import dev.sbs.discordapi.command.exception.permission.BotPermissionException;
import dev.sbs.discordapi.command.exception.permission.PermissionException;
import dev.sbs.discordapi.command.exception.user.UserInputException;
import dev.sbs.discordapi.command.exception.user.UserVerificationException;
import dev.sbs.discordapi.context.command.CommandContext;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.context.message.MessageContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.util.exception.DiscordException;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.util.Permission;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.awt.*;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public abstract class DiscordErrorObject extends DiscordReference {

    private @NotNull Optional<Embed> buildReactiveUserError(ExceptionContext<?> exceptionContext) {
        Optional<Embed> responseBuilder = Optional.empty();

        if (exceptionContext.getException() instanceof SbsApiException sbsApiException) {
            responseBuilder = Optional.of(
                Embed.builder()
                    .withAuthor("Mojang Api Error", getEmoji("CLOUD_DISABLED").map(Emoji::getUrl))
                    .withDescription(sbsApiException.getErrorResponse().getReason())
                    .withFields(
                        Field.builder()
                            .withName("State")
                            .withValue(sbsApiException.getHttpStatus().getState().getTitle())
                            .isInline()
                            .build(),
                        Field.builder()
                            .withName("Code")
                            .withValue(String.valueOf(sbsApiException.getHttpStatus().getCode()))
                            .isInline()
                            .build(),
                        Field.builder()
                            .withName("Message")
                            .withValue(sbsApiException.getHttpStatus().getMessage())
                            .isInline()
                            .build()
                    )
                    .build()
            );
        } else if (exceptionContext.getException() instanceof HypixelApiException hypixelApiException) {
            responseBuilder = Optional.of(
                Embed.builder()
                    .withAuthor("Hypixel Api Error", getEmoji("CLOUD_DISABLED").map(Emoji::getUrl))
                    .withDescription(hypixelApiException.getErrorResponse().getReason())
                    .withFields(
                        Field.builder()
                            .withName("State")
                            .withValue(hypixelApiException.getHttpStatus().getState().getTitle())
                            .isInline()
                            .build(),
                        Field.builder()
                            .withName("Code")
                            .withValue(String.valueOf(hypixelApiException.getHttpStatus().getCode()))
                            .isInline()
                            .build(),
                        Field.builder()
                            .withName("Message")
                            .withValue(hypixelApiException.getHttpStatus().getMessage())
                            .isInline()
                            .build()
                    )
                    .build()
            );
        } else if (exceptionContext.getException() instanceof UserInputException) {
            responseBuilder = Optional.of(
                Embed.builder()
                    .withAuthor("User Input Error", getEmoji("STATUS_IMPORTANT").map(Emoji::getUrl))
                    .withDescription(exceptionContext.getException().getMessage())
                    .withFields((UserInputException) exceptionContext.getException())
                    .build()
            );
        } else if (exceptionContext.getException() instanceof UserVerificationException userVerificationException) {
            String defaultMessage = "You must be verified to run this command!";
            String commandMessage = "You must be verified to run this command without providing a Minecraft Username or UUID!";
            String exceptionMessage = userVerificationException.getMessage();
            boolean useExceptionMessage = (boolean) userVerificationException.getData().getOrDefault("MESSAGE", false);
            boolean useCommandMessage = (boolean) userVerificationException.getData().getOrDefault("COMMAND", false);

            responseBuilder = Optional.of(
                Embed.builder()
                    .withAuthor("User Verification Error", getEmoji("STATUS_IMPORTANT").map(Emoji::getUrl))
                    .withDescription(useExceptionMessage ? exceptionMessage : (useCommandMessage ? commandMessage : defaultMessage))
                    .withFields(userVerificationException)
                    .build()
            );
        } else if (exceptionContext.getException() instanceof ParameterException parameterException) {
            Argument argument = (Argument) parameterException.getData().get("ARGUMENT");
            Parameter parameter = argument.getParameter();
            boolean missing = (boolean) parameterException.getData().get("MISSING");
            String missingDescription = "You did not provide a required parameter.";
            String invalidDescription = "The provided argument does not validate against the expected parameter.";

            Embed.EmbedBuilder embedBuilder = Embed.builder()
                .withAuthor(
                    FormatUtil.format("{0} Parameter", (missing ? "Missing" : "Invalid")),
                    getEmoji("STATUS_INFO").map(Emoji::getUrl)
                )
                .withDescription(missing ? missingDescription : invalidDescription)
                .withFields(
                    Field.builder()
                        .withName("Parameter")
                        .withValue(parameter.getName())
                        .isInline()
                        .build(),
                    Field.builder()
                        .withName("Required")
                        .withValue(parameter.isRequired() ? "Yes" : "No")
                        .isInline()
                        .build(),
                    Field.builder()
                        .withName("Type")
                        .withValue(parameter.getType().name())
                        .isInline()
                        .build()
                )
                .withField(
                    "Description",
                    parameter.getDescription()
                );

            if (!missing)
                embedBuilder.withField(
                    "Argument",
                    argument.getValue().orElse(getEmoji("TEXT_NULL").map(Emoji::asFormat).orElse("*<null>*"))
                );

            responseBuilder = Optional.of(embedBuilder.build());
        } else if (exceptionContext.getException() instanceof HelpCommandException) {
            CommandContext<?> commandContext = (CommandContext<?>) exceptionContext.getEventContext();
            responseBuilder = Optional.of(commandContext.getRelationship().createHelpEmbed(commandContext.isSlashCommand()));
        } else if (exceptionContext.getException() instanceof PermissionException permissionException) {
            boolean botPermissions = (permissionException instanceof BotPermissionException);

            Embed.EmbedBuilder embedBuilder = Embed.builder()
                .withAuthor(FormatUtil.format("Missing {0} Permissions", (botPermissions ? "Bot" : "User")), getEmoji("STATUS_HIGH_IMPORTANCE").map(Emoji::getUrl))
                .withDescription(permissionException.getMessage());

            if (botPermissions) {
                Snowflake snowflake = (Snowflake) permissionException.getData().get("ID");
                Permission[] permissions = (Permission[]) permissionException.getData().get("PERMISSIONS");
                ConcurrentLinkedMap<Permission, Boolean> permissionMap = this.getChannelPermissionMap(snowflake, exceptionContext.getChannel().ofType(GuildChannel.class), permissions);

                embedBuilder.withField(
                        "Required Permissions",
                        StringUtil.join(
                            permissionMap.stream()
                                .filter(entry -> !entry.getValue())
                                .map(Map.Entry::getKey)
                                .map(Permission::name)
                                .collect(Concurrent.toList()),
                            "\n"
                        ),
                        true
                    )
                    .withField(
                        "Status",
                        StringUtil.join(
                            permissionMap.stream()
                                .map(Map.Entry::getValue)
                                .filter(value -> !value)
                                .map(value -> DiscordHelper.getEmoji("ACTION_DENY").map(Emoji::asFormat).orElse("No"))
                                .collect(Concurrent.toList()),
                            "\n"
                        ),
                        true
                    )
                    .withEmptyField(true);
            }

            responseBuilder = Optional.of(embedBuilder.build());
        } else if (exceptionContext.getException() instanceof DisabledCommandException) {
            responseBuilder = Optional.of(
                Embed.builder()
                    .withAuthor("Disabled Command", DiscordHelper.getEmoji("STATUS_DISABLED").map(Emoji::getUrl))
                    .withDescription("This command is currently disabled.")
                    .build()
            );
        }

        return responseBuilder;
    }

    private @NotNull Embed buildDeveloperError(ExceptionContext<?> exceptionContext, Pair<String, Embed> defaultError) {
        String locationValue = "DM";
        String channelValue = "N/A";
        Optional<Snowflake> messageId = Optional.empty();

        // Get Message ID
        if (exceptionContext.getEventContext() instanceof MessageContext)
            messageId = Optional.of(((MessageContext<?>) exceptionContext.getEventContext()).getMessageId());

        // Handle Private Channels
        if (!exceptionContext.isPrivateChannel()) {
            Optional<MessageChannel> messageChannel = exceptionContext.getChannel().blockOptional();

            locationValue = FormatUtil.format(
                "{0}\n{1}",
                exceptionContext.getGuild().map(Guild::getName).blockOptional().orElse("Unknown").replace("`", ""),
                exceptionContext.getGuildId().map(Snowflake::asString).orElse("---")
            );

            channelValue = FormatUtil.format(
                "{0}\n{1}",
                messageChannel.map(MessageChannel::getMention).orElse("Unknown"),
                exceptionContext.getChannelId().asString()
            );
        }

        // Build Log Channel Embed
        Embed.EmbedBuilder logErrorBuilder = defaultError.getRight()
            .mutate()
            .withFields(
                Field.builder()
                    .withName("User")
                    .withValue(
                        "{0}\n{1}",
                        exceptionContext.getInteractUser().getMention(),
                        exceptionContext.getInteractUserId().asString()
                    )
                    .isInline()
                    .build(),
                Field.builder()
                    .withName("Location")
                    .withValue(locationValue)
                    .isInline()
                    .build(),
                Field.builder()
                    .withName("Channel")
                    .withValue(channelValue)
                    .isInline()
                    .build()
            );

        // Handle Message Link
        if (!exceptionContext.isPrivateChannel() && messageId.isPresent())
            logErrorBuilder.withField(
                "Message Link",
                FormatUtil.format(
                    "https://discord.com/channels/{0}/{1}/{2}",
                    exceptionContext.getGuildId().map(Snowflake::asString).orElse("@me"),
                    exceptionContext.getChannelId().asString(),
                    messageId.get().asString()
                )
            );

        // Handle Calling Fields
        exceptionContext.getEmbedBuilderConsumer().ifPresent(consumer -> consumer.accept(logErrorBuilder));

        // Add SimplifiedException Fields
        if (exceptionContext.getException() instanceof SimplifiedException)
            logErrorBuilder.withFields((SimplifiedException) exceptionContext.getException());

        return logErrorBuilder.build();
    }

    private Pair<String, Embed> buildDefaultError(ExceptionContext<?> exceptionContext) {
        String errorId = UUID.randomUUID().toString();

        return Pair.of(errorId, Embed.from(exceptionContext.getException())
            .withColor(Color.DARK_GRAY)
            .withTimestamp(Instant.now())
            .withAuthor(
                "Exception",
                SimplifiedApi.getRepositoryOf(EmojiModel.class)
                    .findFirst(EmojiModel::getKey, "STATUS_HIGH_IMPORTANCE")
                    .flatMap(Emoji::of)
                    .map(Emoji::getUrl)
            )
            .withTitle("Error :: {0}", exceptionContext.getTitle())
            .withField(
                "Error ID",
                errorId
            )
            .build()
        );
    }

    public final <T> Mono<T> handleException(ExceptionContext<?> exceptionContext) {
        // Build Default Error Embed
        Pair<String, Embed> defaultError = this.buildDefaultError(exceptionContext);

        // Handle Reactive Exceptions
        Optional<Embed> reactiveError = this.buildReactiveUserError(exceptionContext);

        // Load User Error
        Embed userError = reactiveError.orElse(defaultError.getRight());

        // Toggle Ephemeral for Slash Commands
        boolean ephemeral = false;

        // Modify Command Errors
        if (exceptionContext.getException() instanceof CommandException) {
            CommandContext<?> commandContext = (CommandContext<?>) exceptionContext.getEventContext();
            ephemeral = commandContext.isSlashCommand();
            String commandPath = commandContext.getRelationship()
                .getInstance()
                .getCommandPath(commandContext.isSlashCommand());

            userError = userError.mutate()
                .withTitle("Command :: {0}", commandPath)
                .build();
        }

        // Handle Notice
        if (reactiveError.isEmpty()) {
            userError = userError.mutate()
                .withField(
                    "Notice",
                    "This error has been automatically reported to the developer."
                )
                .build();
        }

        // Build User Error
        Response userErrorResponse = Response.builder()
            .isInteractable(false)
            .isEphemeral(ephemeral)
            .withReference(exceptionContext.getEventContext())
            .withPages(
                Page.builder()
                    .withEmbeds(userError)
                    .build()
            )
            .build();

        // Send User Error Response & Log Exception Error
        Mono<T> mono = exceptionContext.getEventContext()
            .reply(userErrorResponse)
            .then(
                Mono.justOrEmpty(reactiveError).switchIfEmpty(
                    Mono.just(this.getDiscordBot().getMainGuild())
                        .flatMap(guild -> guild.getChannelById(Snowflake.of(929259633640628224L))) // TODO: Log to SQL configured channel
                        .ofType(MessageChannel.class)
                        .flatMap(messageChannel -> {
                            // Build Exception Response
                            Response logResponse = Response.builder()
                                .isInteractable(false)
                                .withException(exceptionContext.getException())
                                .withPages(
                                    Page.builder()
                                        .withEmbeds(this.buildDeveloperError(exceptionContext, defaultError))
                                        .build()
                                )
                                .build();

                            return Mono.just(messageChannel)
                                .publishOn(logResponse.getReactorScheduler())
                                .flatMap(logResponse::getD4jCreateMono)
                                .then(Mono.empty());
                        })
                )
            )
            .then(Mono.empty());

        // Handle Uncaught Exception
        if (!(exceptionContext.getException() instanceof DiscordException))
            mono = mono.then(Mono.error(exceptionContext.getException()));

        return mono;
    }

}
