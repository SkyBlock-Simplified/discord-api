package dev.sbs.discordapi;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.scheduler.Scheduler;
import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.command.Structure;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.exception.DiscordGatewayException;
import dev.sbs.discordapi.handler.CommandHandler;
import dev.sbs.discordapi.handler.EmojiHandler;
import dev.sbs.discordapi.handler.ExceptionHandler;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.handler.response.ResponseHandler;
import dev.sbs.discordapi.handler.shard.ShardHandler;
import dev.sbs.discordapi.listener.DiscordListener;
import dev.sbs.discordapi.listener.autocomplete.AutoCompleteListener;
import dev.sbs.discordapi.listener.deferrable.command.MessageCommandListener;
import dev.sbs.discordapi.listener.deferrable.command.SlashCommandListener;
import dev.sbs.discordapi.listener.deferrable.command.UserCommandListener;
import dev.sbs.discordapi.listener.deferrable.component.ButtonListener;
import dev.sbs.discordapi.listener.deferrable.component.ModalListener;
import dev.sbs.discordapi.listener.deferrable.component.SelectMenuListener;
import dev.sbs.discordapi.listener.message.MessageCreateListener;
import dev.sbs.discordapi.listener.message.MessageDeleteListener;
import dev.sbs.discordapi.listener.message.reaction.ReactionAddListener;
import dev.sbs.discordapi.listener.message.reaction.ReactionListener;
import dev.sbs.discordapi.listener.message.reaction.ReactionRemoveListener;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.interaction.Modal;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import dev.sbs.discordapi.response.component.interaction.action.SelectMenu;
import dev.sbs.discordapi.response.component.interaction.action.TextInput;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.interaction.MessageInteractionEvent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.event.domain.interaction.UserInteractionEvent;
import discord4j.core.event.domain.lifecycle.ConnectEvent;
import discord4j.core.event.domain.lifecycle.DisconnectEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.MessageDeleteEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.event.domain.message.ReactionRemoveEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.request.RouteMatcher;
import discord4j.rest.response.ResponseFunction;
import discord4j.rest.route.Routes;
import io.netty.channel.unix.Errors;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.SocketException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Discord4J Framework Wrapper for Discord Bots.
 * <br><br>
 * Automatically provides support for the following features when using {@link Response}:
 * <pre>
 * - Caching ({@link ResponseHandler})
 * - Followups ({@link Followup})
 * - Contexts ({@link EventContext})
 * - Command:
 *   - Structure ({@link Structure})
 *   - Implementation ({@link DiscordCommand})
 *   - Building ({@link CommandHandler})
 *   - Parameters ({@link Parameter})
 *   - Processing ({@link SlashCommandListener}, {@link MessageCommandListener}, {@link UserCommandListener})
 *   - Autocomplete ({@link AutoCompleteListener})
 * - Component:
 *   - Interfaces ({@link Component})
 *   - Implementation ({@link Button}, {@link SelectMenu}, {@link Modal}, {@link TextInput})
 *   - Processing ({@link ButtonListener}, {@link SelectMenuListener}, {@link ModalListener})
 * - Reactions {@link ReactionListener}</pre>
 * @see <a href="https://github.com/Discord4J/Discord4J">Discord4J</a>
 */
@Getter
@Log4j2
public abstract class DiscordBot {

    private final @NotNull Scheduler scheduler = new Scheduler();
    private DiscordConfig config;

    // Handlers
    private final @NotNull ExceptionHandler exceptionHandler;
    private final @NotNull ResponseHandler responseHandler;
    private ShardHandler shardHandler;
    private CommandHandler commandHandler;

    // Connection
    private DiscordClient client;
    private GatewayDiscordClient gateway;

    protected void setEmojiHandler(@NotNull Function<String, Optional<Emoji>> locator) {
        EmojiHandler.setLocator(locator);
    }

    protected DiscordBot() {
        this.exceptionHandler = new ExceptionHandler(this);
        this.responseHandler = new ResponseHandler();
        Configurator.setRootLevel(Level.WARN);
    }

    @SuppressWarnings("unchecked")
    public void login(@NotNull DiscordConfig config) {
        this.config = config;
        Configurator.setRootLevel(this.getConfig().getLogLevel());

        log.info("Creating Discord Client");
        this.client = DiscordClientBuilder.create(this.getConfig().getToken())
            .setDefaultAllowedMentions(this.getConfig().getAllowedMentions())
            .onClientResponse(ResponseFunction.emptyIfNotFound()) // Suppress 404 Not Found
            .onClientResponse(ResponseFunction.emptyOnErrorStatus(RouteMatcher.route(Routes.REACTION_CREATE), 400)) // Suppress (Reaction Add) 400 Bad Request
            .onClientResponse(ResponseFunction.retryWhen( // Retry Network Exceptions
                RouteMatcher.any(),
                Retry.backoff(10, Duration.ofSeconds(2))
                    .filter(throwable -> throwable instanceof SocketException || throwable instanceof Errors.NativeIoException))
            )
            .build();

        log.info("Connecting to Discord Gateway");
        this.gateway = this.getClient()
            .gateway()
            .setEnabledIntents(this.getConfig().getIntents())
            .setInitialPresence(this.getConfig()::getClientPresence)
            .setMemberRequestFilter(this.getConfig().getMemberRequestFilter())
            .withEventDispatcher(eventDispatcher -> eventDispatcher.on(ConnectEvent.class)
                .map(ConnectEvent::getClient)
                .flatMap(gatewayDiscordClient -> {
                    log.info("Gateway Connected");
                    this.onGatewayConnected(gatewayDiscordClient);

                    this.getConfig()
                        .getDataConfig()
                        .ifPresent(dataConfig -> {
                            log.info("Creating Database Session");
                            SimplifiedApi.getSessionManager().connect(dataConfig);

                            log.info(
                                "Database Connected. (Initialized in {}ms, Started in {}ms",
                                SimplifiedApi.getSessionManager().getSession().getInitialization(),
                                SimplifiedApi.getSessionManager().getSession().getStartup()
                            );
                            this.onDatabaseConnected();
                        });

                    log.info("Scheduling Cache Cleaner");
                    this.scheduler.scheduleAsync(() -> this.responseHandler.matchAll(CachedResponse::notActive).forEach(entry -> {
                        // Clear Cached Message
                        this.responseHandler.remove(entry);

                        // Clear Message Components and Reactions
                        this.getGateway()
                            .getChannelById(entry.getChannelId())
                            .ofType(MessageChannel.class)
                            .flatMap(channel -> channel.getMessageById(entry.getMessageId()))
                            .flatMap(message -> Mono.just(entry.getResponse())
                                .flatMap(response -> message.removeAllReactions().then(message.edit(
                                    response.mutate()
                                        .clearAllComponents()
                                        .isRenderingPagingComponents(false)
                                        .build()
                                        .getD4jEditSpec()
                                )))
                            )
                            .subscribe();
                    }), 0, 1, TimeUnit.SECONDS);

                    log.info("Registering Event Listeners");
                    ConcurrentList<Publisher<Void>> eventListeners = Concurrent.newList(
                        // Commands
                        eventDispatcher.on(ChatInputAutoCompleteEvent.class, new AutoCompleteListener(this)),
                        eventDispatcher.on(MessageInteractionEvent.class, new MessageCommandListener(this)),
                        eventDispatcher.on(ChatInputInteractionEvent.class, new SlashCommandListener(this)),
                        eventDispatcher.on(UserInteractionEvent.class, new UserCommandListener(this)),

                        // Components
                        eventDispatcher.on(ButtonInteractionEvent.class, new ButtonListener(this)),
                        eventDispatcher.on(ModalSubmitInteractionEvent.class, new ModalListener(this)),
                        eventDispatcher.on(SelectMenuInteractionEvent.class, new SelectMenuListener(this)),

                        // Messages
                        eventDispatcher.on(MessageCreateEvent.class, new MessageCreateListener(this)),
                        eventDispatcher.on(MessageDeleteEvent.class, new MessageDeleteListener(this)),

                        // Reactions
                        eventDispatcher.on(ReactionAddEvent.class, new ReactionAddListener(this)),
                        eventDispatcher.on(ReactionRemoveEvent.class, new ReactionRemoveListener(this)),

                        eventDispatcher.on(DisconnectEvent.class, disconnectEvent -> Mono.fromRunnable(() -> {
                            this.onGatewayDisconnected();
                            this.getScheduler().shutdownNow();
                            SimplifiedApi.getSessionManager().disconnect();
                        }))
                    );

                    this.getConfig().getListeners().forEach(listenerClass -> {
                        DiscordListener<? super Event> discordListener = (DiscordListener<? super Event>) Reflection.of(listenerClass).newInstance(this);
                        eventListeners.add(eventDispatcher.on(discordListener.getEventClass(), discordListener::apply));
                    });

                    log.info("Registering Commands");
                    Mono<Void> commands = (this.commandHandler = CommandHandler.builder(this)
                        .withCommands(this.getConfig().getCommands())
                        .build())
                        .updateApplicationCommands();

                    log.info("Logged in as {}", this.getSelf().getUsername());
                    return Mono.when(eventListeners).and(commands);
                })
            )
            .login()
            .blockOptional()
            .orElseThrow(() -> new DiscordGatewayException("Unable to connect to gateway."));

        this.shardHandler = new ShardHandler(this);
        this.getGateway().onDisconnect().block(); // Stay Online
    }

    public final @NotNull Snowflake getClientId() {
        return this.getGateway().getSelfId();
    }

    public final @NotNull Guild getMainGuild() {
        return this.getGateway()
            .getGuildById(Snowflake.of(this.getConfig().getMainGuildId()))
            .blockOptional()
            .orElseThrow(() -> new DiscordGatewayException("Unable to locate main guild in gateway."));
    }

    public final @NotNull User getSelf() {
        return this.getGateway()
            .getSelf()
            .blockOptional()
            .orElseThrow(() -> new DiscordGatewayException("Unable to locate self in gateway."));
    }

    protected void onDatabaseConnected() { }

    @SuppressWarnings("unused")
    protected void onGatewayConnected(@NotNull GatewayDiscordClient gatewayDiscordClient) { }

    protected void onGatewayDisconnected() { }

}
