package dev.sbs.discordapi;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.scheduler.Scheduler;
import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentSet;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.command.Command;
import dev.sbs.discordapi.command.PrefixCommand;
import dev.sbs.discordapi.listener.DiscordListener;
import dev.sbs.discordapi.listener.command.MessageCommandListener;
import dev.sbs.discordapi.listener.command.SlashCommandListener;
import dev.sbs.discordapi.listener.message.component.ButtonListener;
import dev.sbs.discordapi.listener.message.component.SelectMenuListener;
import dev.sbs.discordapi.listener.message.reaction.ReactionAddListener;
import dev.sbs.discordapi.listener.message.reaction.ReactionRemoveListener;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.util.DiscordConfig;
import dev.sbs.discordapi.util.DiscordLogger;
import dev.sbs.discordapi.util.base.DiscordErrorObject;
import dev.sbs.discordapi.util.cache.DiscordCommandRegistrar;
import dev.sbs.discordapi.util.cache.DiscordResponseCache;
import dev.sbs.discordapi.util.exception.DiscordException;
import dev.sbs.discordapi.util.shard.DiscordShardHandler;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.event.domain.lifecycle.ConnectEvent;
import discord4j.core.event.domain.lifecycle.DisconnectEvent;
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
import io.netty.channel.unix.Errors;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.retry.Retry;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Discord Bot Framework. Powered by Discord4J.
 *
 * @implNote https://github.com/Discord4J/Discord4J
 */
public abstract class DiscordBot extends DiscordErrorObject {

    private static final Pattern tokenValidator = Pattern.compile("[MN][A-Za-z\\d]{23}\\.[\\w-]{6}\\.[\\w-]{27}");

    @Getter private final @NotNull DiscordLogger log;
    @Getter private final @NotNull DiscordClient client;
    @Getter private final @NotNull GatewayDiscordClient gateway;
    @Getter private final @NotNull DiscordShardHandler shardHandler;
    @Getter private final @NotNull DiscordCommandRegistrar commandRegistrar;
    @Getter private final @NotNull Scheduler scheduler = new Scheduler();
    @Getter private final @NotNull DiscordResponseCache responseCache = new DiscordResponseCache();

    @SuppressWarnings("unchecked")
    protected DiscordBot() {
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
            .onClientResponse(ResponseFunction.retryWhen(RouteMatcher.any(), Retry.anyOf(Errors.NativeIoException.class))) // Retry SocketExceptions
            .build();

        this.getLog().info("Registering Commands");
        this.commandRegistrar = DiscordCommandRegistrar.builder(this)
            .withPrefix(this.getPrefixCommand())
            .withCommands(this.getCommands())
            .build();

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
                    .flatMap(message -> Mono.just(entry.getResponse())
                        .flatMap(response -> {
                            // Remove All Reactions
                            Mono<?> handle = message.removeAllReactions();

                            // Remove Non-Preserved Components
                            Response editedResponse = response.mutate()
                                .clearAllComponents()
                                .isRenderingPagingComponents(false)
                                .build();

                            // Update Message Components
                            return handle.then(message.edit(editedResponse.getD4jEditSpec()));
                        })
                    )
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
                    this.getLog().info("Processing Gateway Connected Listener");
                    this.onGatewayConnected(gatewayDiscordClient);

                    this.getLog().info("Connecting to Database");
                    SimplifiedApi.connectDatabase(this.getConfig());
                    this.getLog().debug(
                        "Database Initialized in {0}ms and Cached in {1}ms",
                        SimplifiedApi.getSqlSession().getInitializationTime(),
                        SimplifiedApi.getSqlSession().getStartupTime()
                    );
                    this.getLog().info("Processing Database Connected Listener");
                    this.onDatabaseConnected();

                    this.getLog().info("Registering Built-in Event Listeners");
                    ConcurrentList<Publisher<Void>> eventListeners = Concurrent.newList(
                        eventDispatcher.on(MessageCreateEvent.class, new MessageCommandListener(this)),
                        eventDispatcher.on(ChatInputInteractionEvent.class, new SlashCommandListener(this)),
                        eventDispatcher.on(ButtonInteractionEvent.class, new ButtonListener(this)),
                        eventDispatcher.on(SelectMenuInteractionEvent.class, new SelectMenuListener(this)),
                        eventDispatcher.on(ReactionAddEvent.class, new ReactionAddListener(this)),
                        eventDispatcher.on(ReactionRemoveEvent.class, new ReactionRemoveListener(this)),
                        eventDispatcher.on(DisconnectEvent.class, disconnectEvent -> Mono.fromRunnable(this::onGatewayDisconnected))
                    );

                    this.getLog().info("Registering Custom Event Listeners");
                    this.getListeners().forEach(listenerClass -> {
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

    protected @NotNull ConcurrentList<Class<? extends DiscordListener<? extends Event>>> getListeners() {
        return Concurrent.newUnmodifiableList();
    }

    public final @NotNull Snowflake getClientId() {
        return this.getGateway().getSelfId();
    }

    protected abstract @NotNull ConcurrentSet<Class<? extends Command>> getCommands();

    public abstract @NotNull DiscordConfig getConfig();

    protected abstract @NotNull AllowedMentions getDefaultAllowedMentions();

    public @NotNull IntentSet getDisabledIntents() {
        return IntentSet.of(Intent.GUILD_PRESENCES, Intent.GUILD_MEMBERS);
    }

    @Override
    protected final @NotNull DiscordBot getDiscordBot() {
        return this;
    }

    protected abstract @NotNull ClientPresence getInitialPresence(ShardInfo shardInfo);

    public final @NotNull Guild getMainGuild() {
        return this.getGateway()
            .getGuildById(Snowflake.of(this.getConfig().getMainGuildId()))
            .blockOptional()
            .orElseThrow(() -> SimplifiedException.of(DiscordException.class)
                .withMessage("Unable to locate self in Main Guild!")
                .build()
            );
    }

    protected @NotNull Class<? extends PrefixCommand> getPrefixCommand() {
        return PrefixCommand.class;
    }

    public final @NotNull Command.RootRelationship getRootCommandRelationship() {
        return this.getCommandRegistrar().getRootCommandRelationship();
    }

    public final @NotNull User getSelf() {
        return this.getGateway()
            .getSelf()
            .blockOptional()
            .orElseThrow(() -> SimplifiedException.of(DiscordException.class)
                .withMessage("Unable to locate self in Discord Gateway!")
                .build()
            );
    }

    protected abstract void loadConfig();

    @SuppressWarnings("unused")
    protected void onGatewayConnected(@NotNull GatewayDiscordClient gatewayDiscordClient) { }

    protected void onGatewayDisconnected() { }

    protected void onDatabaseConnected() { }

}
