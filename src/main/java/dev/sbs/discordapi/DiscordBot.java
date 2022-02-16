package dev.sbs.discordapi;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.SimplifiedException;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.scheduler.Scheduler;
import dev.sbs.api.util.concurrent.Concurrent;
import dev.sbs.api.util.concurrent.ConcurrentList;
import dev.sbs.api.util.concurrent.ConcurrentSet;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.command.Command;
import dev.sbs.discordapi.command.PrefixCommand;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.context.message.MessageContext;
import dev.sbs.discordapi.listener.DiscordListener;
import dev.sbs.discordapi.listener.command.MessageCommandListener;
import dev.sbs.discordapi.listener.command.SlashCommandListener;
import dev.sbs.discordapi.listener.message.component.ButtonListener;
import dev.sbs.discordapi.listener.message.component.SelectMenuListener;
import dev.sbs.discordapi.listener.message.reaction.ReactionAddListener;
import dev.sbs.discordapi.listener.message.reaction.ReactionRemoveListener;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.util.DiscordConfig;
import dev.sbs.discordapi.util.DiscordLogger;
import dev.sbs.discordapi.util.DiscordResponseCache;
import dev.sbs.discordapi.util.DiscordShardHandler;
import dev.sbs.discordapi.util.exception.DiscordException;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.event.domain.lifecycle.ConnectEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.event.domain.message.ReactionRemoveEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.presence.ClientPresence;
import discord4j.gateway.ShardInfo;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.request.RouteMatcher;
import discord4j.rest.response.ResponseFunction;
import discord4j.rest.route.Routes;
import discord4j.rest.util.AllowedMentions;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public abstract class DiscordBot {

    private static final Pattern tokenValidator = Pattern.compile("[MN][A-Za-z\\d]{23}\\.[\\w-]{6}\\.[\\w-]{27}");

    @Getter private final DiscordLogger log;
    @Getter private final DiscordClient client;
    @Getter private final GatewayDiscordClient gateway;
    @Getter private final DiscordShardHandler shardHandler;
    @Getter private final Command.RootRelationship rootCommandRelationship;
    @Getter private final Scheduler scheduler = new Scheduler();
    @Getter private final DiscordResponseCache responseCache = new DiscordResponseCache();
    @Getter private Snowflake clientId;
    @Getter private Guild mainGuild;
    @Getter private User self;

    @SuppressWarnings("all")
    protected DiscordBot() { // Discord4J - https://github.com/Discord4J/Discord4J
        this.log = new DiscordLogger(this, this.getClass()); // Initialize Logger

        this.getLog().info("Loading Configuration");
        this.loadConfig();

        this.getLog().info("Validating Discord Token");
        if (StringUtil.isEmpty(this.getConfig().getDiscordToken()))
            throw SimplifiedException.of(DiscordException.class)
                .withMessage("No discord token detected in environment variables!")
                .build();
        else if (!tokenValidator.matcher(this.getConfig().getDiscordToken()).matches())
            throw SimplifiedException.of(DiscordException.class)
                .withMessage("Invalid discord token found!")
                .build();

        this.getLog().info("Creating Discord Client");
        this.client = DiscordClientBuilder.create(this.getConfig().getDiscordToken())
            .setDefaultAllowedMentions(this.getDefaultAllowedMentions())
            .onClientResponse(ResponseFunction.emptyIfNotFound()) // Globally Suppress 404 Not Found
            .onClientResponse(ResponseFunction.emptyOnErrorStatus(RouteMatcher.route(Routes.REACTION_CREATE), 400)) // Globally Suppress 400 Bad Request on Reaction Add
            .build();

        MessageCommandListener commandListener = MessageCommandListener.create(this)
            .withPrefix(this.getPrefixCommand())
            .withCommands(this.getCommands())
            .build();
        this.rootCommandRelationship = commandListener.getRootCommandRelationship();

        this.getLog().info("Scheduling Cache Cleaner");
        this.scheduler.scheduleAsync(() -> this.responseCache.forEach(entry -> {
            if (!entry.isActive()) {
                // Clear Cached Message
                this.responseCache.remove(entry);

                // Clear Message Components and Reactions
                this.getGateway()
                    .getChannelById(entry.getChannelId())
                    .ofType(MessageChannel.class)
                    .flatMap(channel -> channel.getMessageById(entry.getMessageId()))
                    .flatMap(message -> {
                        Mono<?> handle = message.removeAllReactions();
                        Response response = entry.getResponse();

                        // Check For Preserved Components
                        boolean preservedComponent = response.getCurrentPage()
                            .getComponents()
                            .stream()
                            .anyMatch(layoutComponent -> layoutComponent.getComponents()
                                .stream()
                                .anyMatch(Component::isPreserved)
                            );

                        // Handle Preserved Components
                        if (preservedComponent)
                            response = response.mutate().clearComponents().build();

                        // Remove Discord Message Components
                        if (response.isInteractable())
                            handle = handle.then(message.edit(response.getD4jEditSpec()));

                        return handle;
                    })
                    .subscribe();
            }
        }), 0, 1, TimeUnit.SECONDS);

        this.getLog().info("Connecting to Discord Gateway");
        this.gateway = this.getClient()
            .gateway()
            .setDisabledIntents(this.getDisabledIntents())
            .setInitialPresence(this::getInitialPresence)
            .withEventDispatcher(eventDispatcher -> eventDispatcher.on(ConnectEvent.class)
                .map(ConnectEvent::getClient)
                .flatMap(gatewayDiscordClient -> {
                    this.getLog().info("Locating Oneself");
                    this.self = gatewayDiscordClient.getSelf().block();
                    this.clientId = this.getSelf().getId();
                    this.mainGuild = gatewayDiscordClient.getGuildById(Snowflake.of(this.getConfig().getMainGuildId()))
                        .blockOptional()
                        .orElseThrow(() -> SimplifiedException.of(DiscordException.class)
                            .withMessage("Unable to locate self in Main Guild!")
                            .build()
                        );

                    this.getLog().info("Processing Gateway Connected Listener");
                    this.onGatewayConnected(gatewayDiscordClient);

                    this.getLog().info("Connecting to Database");
                    SimplifiedApi.connectDatabase();
                    this.getLog().debug(
                        "Database Initialized in {0}ms and Cached in {1}ms",
                        SimplifiedApi.getSqlSession().getInitializationTime(),
                        SimplifiedApi.getSqlSession().getStartupTime()
                    );
                    this.onDatabaseConnected();

                    this.getLog().info("Registering Built-in Event Listeners");
                    ConcurrentList<Flux<Void>> eventListeners = Concurrent.newList(
                        eventDispatcher.on(MessageCreateEvent.class, commandListener),
                        eventDispatcher.on(ChatInputInteractionEvent.class, new SlashCommandListener(this, gatewayDiscordClient)),
                        eventDispatcher.on(ButtonInteractionEvent.class, new ButtonListener(this)),
                        eventDispatcher.on(SelectMenuInteractionEvent.class, new SelectMenuListener(this)),
                        eventDispatcher.on(ReactionAddEvent.class, new ReactionAddListener(this)),
                        eventDispatcher.on(ReactionRemoveEvent.class, new ReactionRemoveListener(this))
                    );

                    this.getLog().info("Registering Custom Event Listeners");
                    this.getAllListeners().forEach(listenerClass -> {
                        DiscordListener<? super Event> discordListener = (DiscordListener<? super Event>) Reflection.of(listenerClass).newInstance(this);
                        eventListeners.add(eventDispatcher.on(discordListener.getEventClass(), discordListener::apply));
                    });

                    this.getLog().info(FormatUtil.format("Logged in as {0}", this.getSelf().getTag()));
                    return Mono.when(eventListeners);
                })
            )
            .login()
            .blockOptional()
            .orElseThrow(() -> SimplifiedException.of(DiscordException.class)
                .withMessage("Unable to connect to gateway!")
                .build()
            );

        this.shardHandler = new DiscordShardHandler(this);
        this.getGateway().onDisconnect().block(); // Stay Online
    }

    @NotNull
    protected ConcurrentList<Class<? extends DiscordListener<? extends Event>>> getAllListeners() {
        return Concurrent.newUnmodifiableList();
    }

    @NotNull
    protected abstract ConcurrentSet<Class<? extends Command>> getCommands();

    @NotNull
    public abstract DiscordConfig getConfig();

    @NotNull
    protected abstract AllowedMentions getDefaultAllowedMentions();

    @NotNull
    public IntentSet getDisabledIntents() {
        return IntentSet.of(Intent.GUILD_PRESENCES, Intent.GUILD_MEMBERS);
    }

    @NotNull
    protected abstract ClientPresence getInitialPresence(ShardInfo shardInfo);

    protected @NotNull Class<? extends PrefixCommand> getPrefixCommand() {
        return PrefixCommand.class;
    }

    public final void handleUncaughtException(ExceptionContext<?> exceptionContext) {
        String errorId = UUID.randomUUID().toString();
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

        // Send User Error Response
        exceptionContext.reply(
            Response.builder()
                .isInteractable(false)
                .withEmbeds(
                    Embed.from(exceptionContext.getException())
                        .withTitle(exceptionContext.getTitle())
                        .withField(
                            "Error ID",
                            errorId
                        )
                        .withField(
                            "Notice",
                            "This error has been automatically reported to the developer."
                        )
                        .build()
                )
                .withReference(messageId)
                .build()
            );

        // Build Log Channel Embed
        Embed.EmbedBuilder logErrorBuilder = Embed.from(exceptionContext.getException())
            .withTitle(exceptionContext.getTitle())
            .withField(
                "Error ID",
                errorId
            )
            .withFields(
                Field.of(
                    "User",
                    FormatUtil.format(
                        "{0}\n{1}",
                        exceptionContext.getInteractUser().map(User::getMention).blockOptional().orElse("Unknown"),
                        exceptionContext.getInteractUserId().asString()
                    ),
                    true
                ),
                Field.of(
                    "Location",
                    locationValue,
                    true
                ),
                Field.of(
                    "Channel",
                    channelValue,
                    true
                )
            );

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

        // Log Error Message
        // TODO: Log to mysql configured log channel
        Flux.just(this.getMainGuild())
            .flatMap(guild -> guild.getChannelById(Snowflake.of(929259633640628224L)))
            .ofType(MessageChannel.class)
            .flatMap(messageChannel -> {
                Response logResponse = Response.builder()
                    .isInteractable(false)
                    .withException(exceptionContext.getException())
                    .withEmbeds(logErrorBuilder.build())
                    .build();

                return Mono.just(messageChannel)
                    .publishOn(logResponse.getReactorScheduler())
                    .flatMap(logResponse::getD4jCreateMono);
            })
            .subscribe();

        // Print Error Message
        exceptionContext.getException().printStackTrace();
    }

    protected abstract void loadConfig();

    protected void onGatewayConnected(@NotNull GatewayDiscordClient gatewayDiscordClient) { }

    protected void onDatabaseConnected() { }

}
