package dev.sbs.discordapi;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.scheduler.Scheduler;
import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.listener.AutoCompleteListener;
import dev.sbs.discordapi.listener.DiscordListener;
import dev.sbs.discordapi.listener.command.MessageCommandListener;
import dev.sbs.discordapi.listener.command.SlashCommandListener;
import dev.sbs.discordapi.listener.command.UserCommandListener;
import dev.sbs.discordapi.listener.message.MessageCreateListener;
import dev.sbs.discordapi.listener.message.component.ButtonListener;
import dev.sbs.discordapi.listener.message.component.ModalListener;
import dev.sbs.discordapi.listener.message.component.SelectMenuListener;
import dev.sbs.discordapi.listener.message.reaction.ReactionAddListener;
import dev.sbs.discordapi.listener.message.reaction.ReactionRemoveListener;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.util.DiscordCommandRegistrar;
import dev.sbs.discordapi.util.DiscordConfig;
import dev.sbs.discordapi.util.DiscordErrorHandler;
import dev.sbs.discordapi.util.exception.DiscordException;
import dev.sbs.discordapi.util.shard.ShardHandler;
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
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.event.domain.message.ReactionRemoveEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.request.RouteMatcher;
import discord4j.rest.response.ResponseFunction;
import discord4j.rest.route.Routes;
import io.netty.channel.unix.Errors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.SocketException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Discord Bot Framework.
 *
 * @see <a href="https://github.com/Discord4J/Discord4J">Discord4J</a>
 */
@Getter
@Log4j2
public class DiscordBot {

    @Getter(AccessLevel.NONE)
    private final @NotNull DiscordErrorHandler errorHandler;
    private final @NotNull DiscordConfig config;
    private final @NotNull DiscordClient client;
    private final @NotNull GatewayDiscordClient gateway;
    private final @NotNull ShardHandler shardHandler;
    private final @NotNull Scheduler scheduler = new Scheduler();
    private final @NotNull Response.Cache responseCache = new Response.Cache();
    private DiscordCommandRegistrar commandRegistrar;

    @SuppressWarnings("unchecked")
    public DiscordBot(@NotNull DiscordConfig discordConfig) {
        this.errorHandler = new DiscordErrorHandler(this);
        this.config = discordConfig;
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
                    this.getConfig()
                        .getGatewayConnectedEvent()
                        .ifPresent(event -> event.accept(gatewayDiscordClient));

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
                            this.getConfig()
                                .getDatabaseConnectedEvent()
                                .ifPresent(Runnable::run);
                        });

                    log.info("Scheduling Cache Cleaner");
                    this.scheduler.scheduleAsync(() -> this.responseCache.matchAll(Response.Cache.Entry::notActive).forEach(entry -> {
                        // Clear Cached Message
                        this.responseCache.remove(entry);

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
                        eventDispatcher.on(ChatInputInteractionEvent.class, new SlashCommandListener(this)),
                        eventDispatcher.on(ChatInputAutoCompleteEvent.class, new AutoCompleteListener(this)),
                        eventDispatcher.on(UserInteractionEvent.class, new UserCommandListener(this)),
                        eventDispatcher.on(MessageCreateEvent.class, new MessageCreateListener(this)),
                        eventDispatcher.on(MessageInteractionEvent.class, new MessageCommandListener(this)),
                        eventDispatcher.on(ButtonInteractionEvent.class, new ButtonListener(this)),
                        eventDispatcher.on(SelectMenuInteractionEvent.class, new SelectMenuListener(this)),
                        eventDispatcher.on(ModalSubmitInteractionEvent.class, new ModalListener(this)),
                        eventDispatcher.on(ReactionAddEvent.class, new ReactionAddListener(this)),
                        eventDispatcher.on(ReactionRemoveEvent.class, new ReactionRemoveListener(this)),
                        eventDispatcher.on(DisconnectEvent.class, disconnectEvent -> Mono.fromRunnable(() -> {
                            this.getConfig()
                                .getGatewayDisconnectedEvent()
                                .ifPresent(Runnable::run);

                            this.getScheduler().shutdownNow();
                            SimplifiedApi.getSessionManager().disconnect();
                        }))
                    );

                    this.getConfig().getListeners().forEach(listenerClass -> {
                        DiscordListener<? super Event> discordListener = (DiscordListener<? super Event>) Reflection.of(listenerClass).newInstance(this);
                        eventListeners.add(eventDispatcher.on(discordListener.getEventClass(), discordListener::apply));
                    });

                    log.info("Registering Commands");
                    Mono<Void> commands = (this.commandRegistrar = DiscordCommandRegistrar.builder(this)
                        .withCommands(this.getConfig().getCommands())
                        .build())
                        .updateApplicationCommands();

                    log.info("Logged in as {}", this.getSelf().getUsername());
                    return Mono.when(eventListeners).and(commands);
                })
            )
            .login()
            .blockOptional()
            .orElseThrow(() -> SimplifiedException.of(DiscordException.class)
                .withMessage("Unable to connect to gateway!")
                .build()
            );

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
            .orElseThrow(() -> SimplifiedException.of(DiscordException.class)
                .withMessage("Unable to locate self in Main Guild!")
                .build()
            );
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

    public final <T> Mono<T> handleException(ExceptionContext<?> exceptionContext) {
        return this.errorHandler.handleException(exceptionContext);
    }

}
