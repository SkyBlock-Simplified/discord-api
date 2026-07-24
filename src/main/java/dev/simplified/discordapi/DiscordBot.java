package dev.simplified.discordapi;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentSet;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.Structure;
import dev.simplified.discordapi.command.parameter.Argument;
import dev.simplified.discordapi.command.parameter.Parameter;
import dev.simplified.discordapi.component.interaction.TextInput;
import dev.simplified.discordapi.context.EternalBuildContext;
import dev.simplified.discordapi.context.capability.ExceptionContext;
import dev.simplified.discordapi.context.command.AutoCompleteContext;
import dev.simplified.discordapi.context.command.MessageCommandContext;
import dev.simplified.discordapi.context.command.SlashCommandContext;
import dev.simplified.discordapi.context.command.UserCommandContext;
import dev.simplified.discordapi.context.component.ButtonContext;
import dev.simplified.discordapi.context.component.ModalContext;
import dev.simplified.discordapi.context.component.OptionContext;
import dev.simplified.discordapi.context.component.SelectMenuContext;
import dev.simplified.discordapi.context.message.ReactionContext;
import dev.simplified.discordapi.event.BotEvent;
import dev.simplified.discordapi.event.lifecycle.ClientCreatedBotEvent;
import dev.simplified.discordapi.event.lifecycle.GatewayConnectBotEvent;
import dev.simplified.discordapi.event.lifecycle.GatewayDisconnectBotEvent;
import dev.simplified.discordapi.exception.DiscordClientException;
import dev.simplified.discordapi.exception.DiscordGatewayException;
import dev.simplified.discordapi.feature.extractor.ExtractorStore;
import dev.simplified.discordapi.handler.CommandHandler;
import dev.simplified.discordapi.handler.ComponentDispatcher;
import dev.simplified.discordapi.handler.DiscordConfig;
import dev.simplified.discordapi.handler.EmojiHandler;
import dev.simplified.discordapi.handler.LocaleHandler;
import dev.simplified.discordapi.handler.exception.CompositeExceptionHandler;
import dev.simplified.discordapi.handler.exception.DiscordExceptionHandler;
import dev.simplified.discordapi.handler.exception.ExceptionHandler;
import dev.simplified.discordapi.handler.exception.SentryExceptionHandler;
import dev.simplified.discordapi.handler.response.CachedResponse;
import dev.simplified.discordapi.handler.response.CompositeResponseLocator;
import dev.simplified.discordapi.handler.response.EternalResponseLocator;
import dev.simplified.discordapi.handler.response.EternalResponseRepository;
import dev.simplified.discordapi.handler.response.InMemoryResponseLocator;
import dev.simplified.discordapi.handler.response.ResponseExpiryTask;
import dev.simplified.discordapi.handler.response.ResponseLocator;
import dev.simplified.discordapi.handler.shard.ShardHandler;
import dev.simplified.discordapi.listener.BotEventListener;
import dev.simplified.discordapi.listener.DiscordListener;
import dev.simplified.discordapi.listener.EternalComponentListener;
import dev.simplified.discordapi.listener.command.AutoCompleteListener;
import dev.simplified.discordapi.listener.command.MessageCommandListener;
import dev.simplified.discordapi.listener.command.SlashCommandListener;
import dev.simplified.discordapi.listener.command.UserCommandListener;
import dev.simplified.discordapi.listener.component.ComponentListener;
import dev.simplified.discordapi.listener.message.MessageCreateListener;
import dev.simplified.discordapi.listener.message.MessageDeleteListener;
import dev.simplified.discordapi.listener.message.ReactionRemoveListener;
import dev.simplified.discordapi.response.Response;
import dev.simplified.discordapi.response.page.Page;
import dev.simplified.discordapi.response.page.TreePage;
import dev.simplified.discordapi.response.page.editor.EditorPage;
import dev.simplified.reflection.Reflection;
import dev.simplified.scheduler.Scheduler;
import dev.simplified.util.Logging;
import dev.simplified.util.SystemUtil;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.EventDispatcher;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.lifecycle.ConnectEvent;
import discord4j.core.event.domain.lifecycle.DisconnectEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.shard.GatewayBootstrap;
import discord4j.discordjson.json.UserData;
import discord4j.gateway.GatewayOptions;
import discord4j.rest.RestClientBuilder;
import discord4j.rest.request.DefaultRouter;
import discord4j.rest.request.RouteMatcher;
import discord4j.rest.request.RouterOptions;
import discord4j.rest.response.ResponseFunction;
import discord4j.rest.route.Routes;
import io.netty.channel.unix.Errors;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.Log;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

import java.lang.reflect.Modifier;
import java.net.SocketException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Discord4J Framework Wrapper for Discord Bots.
 * <ul>
 *     <li>Commands
 *     <ul>
 *         <li>{@link Structure Immutable API Structure}</li>
 *         <li>{@link CommandHandler Registration & Caching}</li>
 *         <li>{@link DiscordCommand Implementation}
 *         <ul>
 *             <li>Message Commands ({@link MessageCommandContext Context}, {@link MessageCommandListener Listener})</li>
 *             <li>Slash Commands ({@link SlashCommandContext Context}, {@link SlashCommandListener Listener})
 *             <ul>
 *                 <li>{@link Parameter Parameters}</li>
 *                 <li>{@link Argument Arguments}</li>
 *             </ul></li>
 *             <li>User Commands ({@link UserCommandContext Context}, {@link UserCommandListener Listener})</li>
 *             <li>Auto Complete ({@link AutoCompleteContext Context}, {@link AutoCompleteListener Listener})</li>
 *         </ul></li>
 *     </ul></li>
 *     <li>Responses
 *     <ul>
 *         <li>{@link ResponseLocator Registration & Caching}</li>
 *         <li>{@link Page Pages}</li>
 *         <li>Implementations
 *         <ul>
 *             <li>{@link Response}</li>
 *             <li>{@link TreePage}</li>
 *             <li>{@link EditorPage}</li>
 *         </ul></li>
 *         <li>Components
 *         <ul>
 *             <li>Buttons ({@link ButtonContext Context}, {@link ComponentListener Listener})</li>
 *             <li>Modals ({@link ModalContext Context}, {@link TextInput Text Input Context}, {@link ComponentListener Listener})</li>
 *             <li>Select Menus ({@link SelectMenuContext Context}, {@link OptionContext Option Context}, {@link ComponentListener Listener})</li>
 *         </ul></li>
 *         <li>Messages ({@link MessageCreateListener Create Listener}, {@link MessageDeleteListener Delete Listener})</li>
 *         <li>Reactions ({@link ReactionContext Context}, {@link ReactionRemoveListener Add Listener}, {@link ReactionRemoveListener Remove Listener})
 *     </ul></li>
 * </ul>
 * @see <a href="https://github.com/Discord4J/Discord4J">Discord4J</a>
 */
@Getter
@Log
public abstract class DiscordBot {

    private final @NotNull Scheduler scheduler = new Scheduler();
    private final @NotNull DiscordConfig config;

    /**
     * Replay sink for bot-internal lifecycle events. Late subscribers (registered
     * inside {@link #connect()}) receive events emitted earlier from {@link #login()}.
     */
    @Getter(AccessLevel.NONE)
    private final @NotNull Sinks.Many<BotEvent> botEventSink = Sinks.many().replay().limit(16);

    // Handlers
    private final @NotNull ExceptionHandler exceptionHandler;
    private final @NotNull EmojiHandler emojiHandler;
    private final @NotNull LocaleHandler localeHandler;
    private final @NotNull CommandHandler commandHandler;
    private final @NotNull ResponseLocator responseLocator;
    private final @NotNull EternalResponseRepository eternalRepository;
    private final @NotNull ExtractorStore extractorStore;
    private ComponentDispatcher componentDispatcher;
    private ResponseExpiryTask responseExpiryTask;

    // REST
    private DiscordClient client;
    private UserData self;

    // Gateway
    private GatewayDiscordClient gateway;
    private ShardHandler shardHandler;

    protected DiscordBot(@NotNull DiscordConfig config) {
        this.config = config;
        this.exceptionHandler = this.buildExceptionHandler();
        this.emojiHandler = new EmojiHandler(this);
        this.localeHandler = new LocaleHandler(this);
        Logging.setRootLevel(this.getConfig().getLogLevel());

        this.commandHandler = CommandHandler.builder(this)
            .withCommands(this.getConfig().getCommands())
            .withLocaleHandler(this.localeHandler)
            .build();

        this.eternalRepository = config.getEternalRepository();
        this.responseLocator = new CompositeResponseLocator(Concurrent.newList(
            new InMemoryResponseLocator(),
            new EternalResponseLocator(this.eternalRepository, () -> this.componentDispatcher, this)
        ));
        this.extractorStore = config.getExtractorStore();
    }

    /**
     * Establish a connection to the Discord Gateway, enabling real-time events, presence, voice, etc.
     * <ul>
     *   <li>Initializes the Discord Gateway with specified intents, client presence, and member request filters.</li>
     *   <li>Handles the {@link ConnectEvent} to initialize additional components and perform post-connection setup:
     *     <ul>
     *       <li>Emits a {@link GatewayConnectBotEvent} on the internal bot event stream upon a successful connection.</li>
     *       <li>Schedules a periodic task to clean up inactive cached responses and update message states.</li>
     *       <li>Registers event listeners dynamically by scanning resources and loading implementations of
     *           {@link DiscordListener} and {@link BotEventListener}, including any user-defined listeners from
     *           the configuration.</li>
     *       <li>Subscribes a bridge for Discord4J's {@link DisconnectEvent} that emits a
     *           {@link GatewayDisconnectBotEvent} on the internal bot event stream.</li>
     *       <li>Registers and uploads custom emojis using the configured emoji handler.</li>
     *       <li>Updates global application commands through the command handler.</li>
     *     </ul>
     *   </li>
     *   <li>Logs the bot's username after successfully logging in.</li>
     * </ul>
     * <p>
     * Waits for manual gateway termination to remain online and operational indefinitely.
     *
     * @throws DiscordGatewayException If unable to connect to the Discord Gateway.
     */
    protected final void connect() throws DiscordGatewayException {
        if (this.gateway != null)
            throw new IllegalStateException("Discord Gateway already connected");

        log.info("Connecting to Discord Gateway");
        GatewayBootstrap<GatewayOptions> bootstrap = this.getClient()
            .gateway()
            .setEnabledIntents(this.getConfig().getIntents())
            .setInitialPresence(this.getConfig()::getClientPresence)
            .setMemberRequestFilter(this.getConfig().getMemberRequestFilter())
            .withEventDispatcher(eventDispatcher -> eventDispatcher.on(ConnectEvent.class)
                .map(ConnectEvent::getClient)
                .flatMap(gatewayDiscordClient -> {
                    log.info("Gateway Connected");
                    this.emitBotEvent(new GatewayConnectBotEvent(this, gatewayDiscordClient));

                    ConcurrentSet<Class<? extends EternalComponentListener>> eternalListenerClasses = Reflection.getResources()
                        .filterPackage(EternalComponentListener.class)
                        .getSubtypesOf(EternalComponentListener.class)
                        .stream()
                        .filter(listenerClass -> !Modifier.isAbstract(listenerClass.getModifiers()))
                        .collect(Concurrent.toSet());

                    this.componentDispatcher = new ComponentDispatcher(
                        this,
                        this.getCommandHandler().getLoadedCommands(),
                        eternalListenerClasses
                    );

                    log.info("Scheduling Cache Cleaner");
                    this.responseExpiryTask = new ResponseExpiryTask(this);
                    this.responseExpiryTask.start();

                    log.info("Registering Event Listeners");
                    ConcurrentList<Publisher<Void>> eventListeners = Reflection.getResources()
                        .filterPackage(DiscordListener.class)
                        .getSubtypesOf(DiscordListener.class)
                        .stream()
                        .filter(listenerClass -> !Modifier.isAbstract(listenerClass.getModifiers()))
                        .map(listenerClass -> this.createListener(eventDispatcher, listenerClass))
                        .collect(Concurrent.toList());

                    this.getConfig()
                        .getListeners()
                        .stream()
                        .map(listenerClass -> this.createListener(eventDispatcher, listenerClass))
                        .forEach(eventListeners::add);

                    Reflection.getResources()
                        .filterPackage(BotEventListener.class)
                        .getSubtypesOf(BotEventListener.class)
                        .stream()
                        .filter(listenerClass -> !Modifier.isAbstract(listenerClass.getModifiers()))
                        .map(this::createBotEventListener)
                        .forEach(eventListeners::add);

                    this.getConfig()
                        .getBotEventListeners()
                        .stream()
                        .map(this::createBotEventListener)
                        .forEach(eventListeners::add);

                    eventListeners.add(eventDispatcher.on(DisconnectEvent.class, event -> {
                        this.emitBotEvent(new GatewayDisconnectBotEvent(this));
                        return Mono.empty();
                    }));

                    log.info("Logged in as {}", this.getSelf().username());
                    return Mono.when(eventListeners)
                        .and(this.getCommandHandler().updateApplicationCommands())
                        .and(this.getEmojiHandler().sync());
                })
            );

        // Optional gateway client factory (offline test harness injects a fake in-JVM GatewayClient);
        // defaults to the stock live gateway connection
        this.gateway = this.getConfig().getGatewayClientFactory()
            .map(factory -> bootstrap.login(factory))
            .orElseGet(() -> bootstrap.login())
            .blockOptional()
            .orElseThrow(() -> new DiscordGatewayException("Unable to connect to gateway."));

        this.shardHandler = new ShardHandler(this);
        this.getGateway().onDisconnect().block(); // Stay Online
    }

    /**
     * Initializes and configures the Discord REST Client, allowing for REST-only API usage.
     * <ul>
     *   <li>Creates a Discord client using the token provided.</li>
     *   <li>Sets the default allowed mentions for the client.</li>
     *   <li>Suppresses certain client responses:
     *     <ul>
     *       <li>404 Not Found responses are ignored.</li>
     *       <li>400 Bad Request responses for reaction creation are suppressed.</li>
     *     </ul>
     *   </li>
     *   <li>Implements retry logic for network exceptions such as {@code SocketException}
     *       or {@code NativeIoException}, with exponential backoff up to 10 retries.</li>
     * </ul>
     */
    protected final void login() {
        if (this.client != null)
            throw new IllegalStateException("Discord Client already initialized.");

        log.info("Creating Discord Client");
        RestClientBuilder<DiscordClient, RouterOptions> clientBuilder = DiscordClientBuilder.create(this.getConfig().getToken())
            .setDefaultAllowedMentions(this.getConfig().getAllowedMentions())
            .onClientResponse(ResponseFunction.emptyIfNotFound()) // Suppress 404 Not Found
            .onClientResponse(ResponseFunction.emptyOnErrorStatus(RouteMatcher.route(Routes.REACTION_CREATE), 400)) // Suppress (Reaction Add) 400 Bad Request
            .onClientResponse(ResponseFunction.retryWhen( // Retry Network Exceptions
                RouteMatcher.any(),
                Retry.backoff(10, Duration.ofSeconds(2))
                    .filter(throwable -> throwable instanceof SocketException || throwable instanceof Errors.NativeIoException))
            );

        // Optional custom REST transport (e.g. non-secure HttpClient for a plaintext local endpoint)
        this.getConfig().getRestReactorResources().ifPresent(clientBuilder::setReactorResources);

        // Optional custom API base url (custom endpoint / self-host / proxy / offline test server); the
        // gateway endpoint is resolved from this base url via GET /gateway, so both are redirected together
        this.client = this.getConfig().getApiBaseUrl()
            .map(baseUrl -> clientBuilder.build(options -> new DefaultRouter(new RouterOptions(
                options.getToken(),
                options.getReactorResources(),
                options.getExchangeStrategies(),
                options.getResponseTransformers(),
                options.getGlobalRateLimiter(),
                options.getRequestQueueFactory(),
                baseUrl
            ))))
            .orElseGet(clientBuilder::build);

        this.self = this.client.getSelf()
            .blockOptional()
            .orElseThrow(() -> new DiscordClientException("Unable to locate self."));

        this.emitBotEvent(new ClientCreatedBotEvent(this, this.client));
    }

    public final @NotNull DiscordClient getClient() {
        if (this.client == null)
            throw new IllegalStateException("Discord Client not initialized.");

        return this.client;
    }

    public final @NotNull Snowflake getClientId() {
        return this.getClient().getCoreResources().getSelfId();
    }

    public final @NotNull GatewayDiscordClient getGateway() {
        if (this.gateway == null)
            throw new IllegalStateException("Discord Gateway not connected");

        return this.gateway;
    }

    public final @NotNull Guild getMainGuild() {
        return this.getGateway()
            .getGuildById(Snowflake.of(this.getConfig().getMainGuildId()))
            .blockOptional()
            .orElseThrow(() -> new DiscordGatewayException("Unable to locate main guild."));
    }

    /**
     * Re-renders an eternal response from its cold record, editing the backing Discord message in
     * place without needing a hot-tier entry. Rebuilds the response from the registered
     * {@link dev.simplified.discordapi.listener.Eternal @Eternal} builder, restores the persisted
     * navigation coordinate, and applies the edit. A no-op when no record exists or its builder is
     * no longer registered.
     *
     * @param responseId the stable id of the eternal response to refresh
     * @return a {@link Mono} completing when the message has been re-rendered
     */
    public final @NotNull Mono<Void> refreshEternal(@NotNull UUID responseId) {
        return this.eternalRepository.findByResponseId(responseId)
            .flatMap(record -> {
                Optional<ComponentDispatcher.EternalRoute> builder = this.componentDispatcher.findEternalBuilder(record.builderKey());

                if (builder.isEmpty()) {
                    log.warn("No @Eternal builder registered for key '{}' (response {}); skipping refresh", record.builderKey(), responseId);
                    return Mono.empty();
                }

                Mono<Message> message = this.getGateway()
                    .getChannelById(record.channelId())
                    .ofType(MessageChannel.class)
                    .flatMap(channel -> channel.getMessageById(record.messageId()));

                return Mono.zip(message, this.getGateway().getUserById(record.userId()))
                    .flatMap(tuple -> {
                        EternalBuildContext buildContext = EternalBuildContext.ofRefresh(
                            this,
                            record.channelId(),
                            record.guildId(),
                            tuple.getT2(),
                            record.responseId(),
                            record.payload()
                        );

                        Response built = this.componentDispatcher.invokeEternalBuilder(builder.get(), buildContext);
                        Response hydrated = built.mutate()
                            .withUniqueId(record.responseId())
                            .asEternal(record.builderKey(), record.payload())
                            .build();
                        record.navState().applyTo(hydrated.getHistoryHandler());

                        return tuple.getT1().edit(hydrated.getD4jEditSpec(this.getEmojiHandler())).then();
                    });
            });
    }

    /**
     * Instantiates the given {@link DiscordListener} subclass and registers it
     * with the event dispatcher, wrapping it with top-level error handling that
     * forwards unhandled exceptions to the exception handler.
     *
     * @param <T> the Discord4J event type
     * @param eventDispatcher the event dispatcher to register with
     * @param listenerClass the listener class to instantiate and register
     * @return a publisher completing when the listener subscription ends
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <T extends Event> @NonNull Publisher<Void> createListener(@NotNull EventDispatcher eventDispatcher, @NotNull Class<? extends DiscordListener> listenerClass) {
        DiscordListener<T> instance = (DiscordListener<T>) new Reflection<>(listenerClass).newInstance(this);
        return eventDispatcher.on(instance.getEventClass(), event ->
            Mono.from(instance.apply(event)).onErrorResume(throwable -> this.getExceptionHandler().handleException(
                ExceptionContext.of(this, event, throwable, instance.getTitle() + " Exception")
            ))
        );
    }

    /**
     * Instantiates the given {@link BotEventListener} subclass and subscribes it
     * to the internal {@link #botEventSink bot event stream}, filtering by the
     * listener's resolved event type and logging any errors locally.
     *
     * @param <T> the bot event type
     * @param listenerClass the listener class to instantiate and subscribe
     * @return a publisher completing when the underlying sink completes
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <T extends BotEvent> @NonNull Publisher<Void> createBotEventListener(@NotNull Class<? extends BotEventListener> listenerClass) {
        BotEventListener<T> instance = (BotEventListener<T>) new Reflection<>(listenerClass).newInstance(this);
        return this.botEventSink.asFlux()
            .ofType(instance.getEventClass())
            .flatMap(event -> Mono.from(instance.apply(event))
                .onErrorResume(throwable -> {
                    log.error(
                        "{} threw while handling {}",
                        instance.getTitle(),
                        event.getClass().getSimpleName(),
                        throwable
                    );
                    return Mono.empty();
                }))
            .then();
    }

    /**
     * Pushes the given event onto the internal bot event stream, where it will
     * be delivered to every subscribed {@link BotEventListener} whose declared
     * event type is assignable from {@code event}.
     *
     * @param event the event to emit
     */
    private void emitBotEvent(@NotNull BotEvent event) {
        Sinks.EmitResult result = this.botEventSink.tryEmitNext(event);

        if (result.isFailure())
            log.warn("Failed to emit bot event {}: {}", event.getClass().getSimpleName(), result);
    }

    /**
     * Builds the exception handler chain based on configuration. Adds a
     * {@link SentryExceptionHandler} if a Sentry DSN is available (config
     * takes priority over {@code SENTRY_DSN} environment variable), and a
     * {@link DiscordExceptionHandler} if a debug channel is configured.
     *
     * @return the configured exception handler
     */
    private @NotNull ExceptionHandler buildExceptionHandler() {
        ConcurrentList<ExceptionHandler> handlers = Concurrent.newList();

        // Resolve Sentry DSN: config > env var
        this.config.getSentryDsn()
            .or(() -> SystemUtil.getEnv("SENTRY_DSN"))
            .ifPresent(dsn -> handlers.add(new SentryExceptionHandler(this, dsn)));

        // Add Discord handler
        handlers.add(this.config.getLogChannelId()
            .map(channelId -> new DiscordExceptionHandler(this, channelId))
            .orElse(new DiscordExceptionHandler(this, -1L)));

        if (handlers.size() == 1)
            return handlers.getFirst();

        return new CompositeExceptionHandler(this, handlers);
    }

    /**
     * Starts the bot by executing the full two-phase initialization lifecycle.
     * <p>
     * <b>Phase 1 - REST Client ({@link #login()})</b>
     * <ul>
     *     <li>Creates and configures the {@link DiscordClient} with the bot token, allowed mentions,
     *         response suppression rules, and network retry logic.</li>
     *     <li>Fetches the bot's own {@link UserData} from Discord.</li>
     *     <li>Emits a {@link ClientCreatedBotEvent} on the internal bot event stream.</li>
     * </ul>
     * <p>
     * <b>Phase 2 - Gateway ({@link #connect()})</b>
     * <ul>
     *     <li>Opens a Gateway connection with the configured intents, presence, and member request filter.</li>
     *     <li>On the initial {@link ConnectEvent}:
     *     <ul>
     *         <li>Emits a {@link GatewayConnectBotEvent} on the internal bot event stream.</li>
     *         <li>Schedules a periodic cache cleaner that removes inactive {@link CachedResponse} entries.</li>
     *         <li>Discovers and registers all {@link DiscordListener} and {@link BotEventListener} implementations.</li>
     *         <li>Subscribes a bridge for Discord4J's {@link DisconnectEvent} that emits a {@link GatewayDisconnectBotEvent}.</li>
     *         <li>Syncs custom emojis via the {@link EmojiHandler}.</li>
     *         <li>Updates global application commands via the {@link CommandHandler}.</li>
     *     </ul></li>
     *     <li>Blocks the calling thread on {@link GatewayDiscordClient#onDisconnect()} to keep the bot online
     *         until the gateway is terminated.</li>
     * </ul>
     *
     * @throws DiscordGatewayException if the gateway connection cannot be established
     * @see #login()
     * @see #connect()
     */
    protected final void start() {
        this.login();
        this.connect();
    }

}
