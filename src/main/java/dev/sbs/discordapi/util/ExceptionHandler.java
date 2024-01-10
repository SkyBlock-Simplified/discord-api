package dev.sbs.discordapi.util;

import dev.sbs.api.client.exception.ApiException;
import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.linked.ConcurrentLinkedMap;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.api.util.mutable.pair.Pair;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.exception.DisabledCommandException;
import dev.sbs.discordapi.command.exception.InvalidParameterException;
import dev.sbs.discordapi.command.exception.permission.BotPermissionException;
import dev.sbs.discordapi.command.exception.permission.PermissionException;
import dev.sbs.discordapi.command.exception.user.UserInputException;
import dev.sbs.discordapi.command.exception.user.UserVerificationException;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.context.MessageContext;
import dev.sbs.discordapi.context.deferrable.command.CommandContext;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.embed.structure.Author;
import dev.sbs.discordapi.response.embed.structure.Field;
import dev.sbs.discordapi.response.embed.structure.Footer;
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

public final class ExceptionHandler extends DiscordReference {

    public ExceptionHandler(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    private @NotNull Optional<Embed> buildReactiveUserError(ExceptionContext<?> exceptionContext) {
        Optional<Embed> responseBuilder = Optional.empty();

        if (exceptionContext.getException() instanceof ApiException apiException) {
            responseBuilder = Optional.of(
                Embed.builder()
                    .withAuthor(
                        Author.builder()
                            .withName("%s Api Error", apiException.getName())
                            .withIconUrl(getEmoji("CLOUD_DISABLED").map(Emoji::getUrl))
                            .build()
                    )
                    .withDescription(apiException.getResponse().getReason())
                    .withFields(
                        Field.builder()
                            .withName("State")
                            .withValue(apiException.getStatus().getState().getTitle())
                            .isInline()
                            .build(),
                        Field.builder()
                            .withName("Code")
                            .withValue(String.valueOf(apiException.getStatus().getCode()))
                            .isInline()
                            .build(),
                        Field.builder()
                            .withName("Message")
                            .withValue(apiException.getStatus().getMessage())
                            .isInline()
                            .build()
                    )
                    .build()
            );
        } else if (exceptionContext.getException() instanceof UserInputException) {
            responseBuilder = Optional.of(
                Embed.builder()
                    .withAuthor(
                        Author.builder()
                            .withName("User Input Error")
                            .withIconUrl(getEmoji("STATUS_IMPORTANT").map(Emoji::getUrl))
                            .build())
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
                    .withAuthor(
                        Author.builder()
                            .withName("User Verification Error")
                            .withIconUrl(getEmoji("STATUS_IMPORTANT").map(Emoji::getUrl))
                            .build()
                    )
                    .withDescription(useExceptionMessage ? exceptionMessage : (useCommandMessage ? commandMessage : defaultMessage))
                    .withFields(userVerificationException)
                    .build()
            );
        } else if (exceptionContext.getException() instanceof InvalidParameterException parameterException) {
            Parameter parameter = (Parameter) parameterException.getData().get("PARAMETER");
            String value = (String) parameterException.getData().get("VALUE");

            Embed.Builder builder = Embed.builder()
                .withAuthor(
                    Author.builder()
                        .withName("Invalid Parameter")
                        .withIconUrl(getEmoji("STATUS_INFO").map(Emoji::getUrl))
                        .build()
                )
                .withDescription("The provided argument does not validate against the expected parameter.")
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
                )
                .withField(
                    "Argument",
                    value
                );

            responseBuilder = Optional.of(builder.build());
        } else if (exceptionContext.getException() instanceof PermissionException permissionException) {
            boolean botPermissions = (permissionException instanceof BotPermissionException);

            Embed.Builder builder = Embed.builder()
                .withAuthor(
                    Author.builder()
                        .withName("Missing %s Permissions", (botPermissions ? "Bot" : "User"))
                        .withIconUrl(getEmoji("STATUS_HIGH_IMPORTANCE").map(Emoji::getUrl))
                        .build()
                )
                .withDescription(permissionException.getMessage());

            if (botPermissions) {
                Snowflake snowflake = (Snowflake) permissionException.getData().get("ID");
                Permission[] permissions = (Permission[]) permissionException.getData().get("PERMISSIONS");
                ConcurrentLinkedMap<Permission, Boolean> permissionMap = this.getChannelPermissionMap(snowflake, exceptionContext.getChannel().ofType(GuildChannel.class), permissions);

                builder.withField(
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
                                .map(value -> getEmoji("ACTION_DENY").map(Emoji::asFormat).orElse("No"))
                                .collect(Concurrent.toList()),
                            "\n"
                        ),
                        true
                    )
                    .withEmptyField(true);
            }

            responseBuilder = Optional.of(builder.build());
        } else if (exceptionContext.getException() instanceof DisabledCommandException) {
            responseBuilder = Optional.of(
                Embed.builder()
                    .withAuthor(
                        Author.builder()
                            .withName("Disabled Command")
                            .withIconUrl(getEmoji("STATUS_DISABLED").map(Emoji::getUrl))
                            .build()
                    )
                    .withDescription("This command is currently disabled.")
                    .build()
            );
        }

        return responseBuilder;
    }

    private @NotNull Embed buildDeveloperError(ExceptionContext<?> exceptionContext, Pair<String, Embed> defaultError, Optional<Snowflake> messageId) {
        String locationValue = "DM";
        String channelValue = "N/A";

        // Handle Private Channels
        if (!exceptionContext.isPrivateChannel()) {
            Optional<MessageChannel> messageChannel = exceptionContext.getChannel().blockOptional();
            String location = exceptionContext.getGuild()
                .map(Guild::getName)
                .blockOptional()
                .orElse("Unknown")
                .replace("`", "");

            locationValue = String.format(
                "%s\n%s",
                exceptionContext.getGuildId().isPresent() ?
                    String.format(
                        "[%s](%s)",
                        location,
                        String.format("https://discord.com/servers/%s", exceptionContext.getGuildId().get().asString())
                    ) : location,
                exceptionContext.getGuildId()
                    .map(Snowflake::asString)
                    .orElse("---")
            );

            channelValue = String.format(
                "%s\n%s",
                messageChannel.map(MessageChannel::getMention).orElse("Unknown"),
                exceptionContext.getChannelId().asString()
            );
        }

        // Build Log Channel Embed
        Embed.Builder logErrorBuilder = defaultError.getRight()
            .mutate()
            .clearFields()
            .withField(
                "Error ID",
                (!exceptionContext.isPrivateChannel() && messageId.isPresent()) ?
                    String.format(
                        "[%s](%s)",
                        defaultError.getLeft(),
                        String.format(
                            "https://discord.com/channels/%s/%s/%s",
                            exceptionContext.getGuildId().map(Snowflake::asString).orElse("@me"),
                            exceptionContext.getChannelId().asString(),
                            messageId.get().asString()
                        )
                    ) :
                    defaultError.getLeft()
            )
            .withFields(
                Field.builder()
                    .withName("User")
                    .withValue(
                        "%s\n%s",
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
            .withAuthor(
                Author.builder()
                    .withName("Exception")
                    .withIconUrl(getEmoji("STATUS_HIGH_IMPORTANCE").map(Emoji::getUrl))
                    .build()
            )
            .withTitle("Error :: %s", exceptionContext.getTitle())
            .withField(
                "Error ID",
                errorId
            )
            .withFooter(
                Footer.builder()
                    .withTimestamp(Instant.now())
                    .build()
            )
            .build()
        );
    }

    public <T> @NotNull Mono<T> handleException(@NotNull ExceptionContext<?> exceptionContext) {
        // Build Default Error Embed
        Pair<String, Embed> defaultError = this.buildDefaultError(exceptionContext);

        // Handle User Only Errors
        Optional<Embed> userReactiveError = this.buildReactiveUserError(exceptionContext);

        // Load User Error
        Embed userError = userReactiveError.orElse(defaultError.getRight());

        // Modify Command Errors
        if (exceptionContext.getEventContext() instanceof CommandContext<?> commandContext) {
            userError = userError.mutate()
                .withTitle("Command :: %s", commandContext.getCommand().getName())
                .build();
        }

        // Handle Notice
        if (userReactiveError.isEmpty()) {
            userError = userError.mutate()
                .withField(
                    "Notice",
                    "This error has been automatically reported to the developer."
                )
                .build();
        }

        // Build User Error
        Response userErrorResponse = Response.builder()
            .isEphemeral(true)
            .withPages(
                Page.builder()
                    .withEmbeds(userError)
                    .build()
            )
            .build();

        // Send User Error Response & Log Exception Error
        Mono<Void> reply = exceptionContext.getEventContext() instanceof MessageContext ?
            ((MessageContext<?>) exceptionContext.getEventContext()).followup(userErrorResponse) :
            exceptionContext.reply(userErrorResponse);

        Mono<T> mono = reply.then(Mono.justOrEmpty(userReactiveError).switchIfEmpty(
            // Log to debug channel when it's not an expected reactive user error
            Mono.just(this.getDiscordBot().getMainGuild())
                .flatMap(guild -> guild.getChannelById(Snowflake.of(
                    this.getDiscordBot()
                        .getConfig()
                        .getDebugChannelId()
                        .orElse(-1L)
                )))
                .ofType(MessageChannel.class)
                .flatMap(messageChannel -> {
                    // Get Message ID
                    Optional<Snowflake> messageId;

                    if (exceptionContext.getEventContext() instanceof MessageContext)
                        messageId = Optional.of(((MessageContext<?>) exceptionContext.getEventContext()).getMessageId());
                    else
                        messageId = this.getDiscordBot()
                            .getResponseCache()
                            .findFirst(entry -> entry.getResponse().getUniqueId(), userErrorResponse.getUniqueId())
                            .map(Response.Cache.Entry::getMessageId);

                    // Build Exception Response
                    Response logResponse = Response.builder()
                        .withException(exceptionContext.getException())
                        .withPages(
                            Page.builder()
                                .withEmbeds(this.buildDeveloperError(exceptionContext, defaultError, messageId))
                                .build()
                        )
                        .build();

                    return Mono.just(messageChannel)
                        .publishOn(logResponse.getReactorScheduler())
                        .flatMap(logResponse::getD4jCreateMono)
                        .doOnNext(__ -> messageId.ifPresent(id -> this.getDiscordBot()
                            .getResponseCache()
                            .removeIf(entry -> entry.getMessageId().equals(id))
                        ))
                        .then(Mono.empty());
                })
            ))
            .then(Mono.empty());

        // Handle Uncaught Exception
        if (!(exceptionContext.getException() instanceof DiscordException))
            mono = mono.then(Mono.error(exceptionContext.getException()));

        return mono;
    }

}
