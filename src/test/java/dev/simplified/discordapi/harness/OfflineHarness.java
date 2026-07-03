package dev.simplified.discordapi.harness;

import dev.simplified.discordapi.handler.DiscordConfig;
import dev.simplified.discordapi.harness.gateway.DispatchFactory;
import dev.simplified.discordapi.harness.gateway.FakeGatewayClient;
import dev.simplified.discordapi.harness.gateway.SlashOption;
import dev.simplified.discordapi.harness.rest.LocalDiscordServer;
import dev.simplified.discordapi.harness.rest.RecordedRequest;
import dev.simplified.util.Logging;
import discord4j.common.ReactorResources;
import discord4j.common.util.Snowflake;
import discord4j.discordjson.json.gateway.Dispatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Wires the offline pieces together: a localhost REST mock, a fake in-JVM gateway, and a
 * {@link HarnessBot} pointed at both. Tests boot it, wait until connected, then drive dispatches and
 * assert on the captured REST traffic.
 */
public final class OfflineHarness implements AutoCloseable {

    private final HarnessConfig config;
    private final LocalDiscordServer server;
    private final FakeGatewayClient gatewayClient;
    private final DispatchFactory dispatchFactory;
    private final HarnessBot bot;

    /** Boots with the standard harness identity ({@code HarnessConfig.builder().build()}). */
    public OfflineHarness() {
        this(HarnessConfig.builder().build());
    }

    /**
     * Boots with the given harness identity, threading it through the REST mock, the dispatch factory, and
     * the bot's {@link DiscordConfig}.
     *
     * @param config the harness identity to use
     */
    public OfflineHarness(@NotNull HarnessConfig config) {
        this.config = config;
        this.dispatchFactory = new DispatchFactory(config);
        this.server = new LocalDiscordServer(config).start();

        List<Dispatch> handshake = this.dispatchFactory.handshake(config.getBotId());
        this.gatewayClient = new FakeGatewayClient(handshake, 1);

        ReactorResources plaintextRest = ReactorResources.builder()
            .httpClient(HttpClient.create().compress(true).followRedirect(true)) // no .secure() -> plaintext http
            .build();

        DiscordConfig discordConfig = DiscordConfig.builder()
            .withToken(config.getToken())
            .withMainGuildId(config.getGuildId())
            .withCommands("dev.simplified.discordapi.harness.command")
            .withApiBaseUrl(this.server.baseUrl())
            .withRestReactorResources(plaintextRest)
            .withGatewayClientFactory(options -> this.gatewayClient)
            .withLogLevel(Logging.Level.INFO)
            .build();

        this.bot = new HarnessBot(discordConfig);
    }

    /** The harness identity backing this run (ids, token, command-id scheme). */
    public @NotNull HarnessConfig config() {
        return this.config;
    }

    /**
     * Boots the bot and blocks until the gateway reports connected or the timeout elapses.
     *
     * @param timeout the maximum time to wait for connection
     * @return this harness
     */
    public @NotNull OfflineHarness boot(@NotNull Duration timeout) {
        this.bot.bootAsync();
        awaitTrue(this::gatewayConnected, timeout, "gateway did not connect");
        return this;
    }

    public @NotNull FakeGatewayClient gateway() {
        return this.gatewayClient;
    }

    /**
     * Blocks until the named command has been assigned its Discord id (i.e. bulk-overwrite completed and
     * the id mapping was populated), so a simulated interaction can route to it.
     *
     * @param name the command name
     * @param timeout the maximum time to wait
     * @return this harness
     */
    public @NotNull OfflineHarness awaitCommandRegistered(@NotNull String name, @NotNull Duration timeout) {
        long commandId = this.config.commandId(name);
        awaitTrue(() -> !this.bot.getCommandHandler().getCommandsById(commandId).isEmpty(), timeout, "command '" + name + "' not registered");
        return this;
    }

    /**
     * Waits for the named slash command to be registered, then pushes a simulated {@code type 2}
     * interaction for it - carrying the given resolved options - into the live dispatch pipeline.
     *
     * @param name the slash command name
     * @param options the resolved top-level options, if any
     * @return this harness
     */
    public @NotNull OfflineHarness sendSlashCommand(@NotNull String name, @NotNull SlashOption... options) {
        this.awaitCommandRegistered(name, Duration.ofSeconds(10));
        this.gatewayClient.emit(this.dispatchFactory.slashCommand(name, options));
        return this;
    }

    /**
     * Waits for the parent command to be registered, then pushes a simulated bare-subcommand interaction
     * ({@code parent sub options}) into the live dispatch pipeline. The whole tree registers under the
     * parent, so registration is awaited on the parent name.
     *
     * @param parent the parent command name
     * @param sub the subcommand name
     * @param options the resolved leaf options, if any
     * @return this harness
     */
    public @NotNull OfflineHarness sendSubCommand(@NotNull String parent, @NotNull String sub, @NotNull SlashOption... options) {
        return this.sendSubCommand(parent, null, sub, options);
    }

    /**
     * Waits for the parent command to be registered, then pushes a simulated grouped-subcommand interaction
     * ({@code parent group sub options}) into the live dispatch pipeline. The whole tree registers under the
     * parent, so registration is awaited on the parent name.
     *
     * @param parent the parent command name
     * @param group the subcommand group name, or {@code null}/blank for a bare subcommand
     * @param sub the subcommand name
     * @param options the resolved leaf options, if any
     * @return this harness
     */
    public @NotNull OfflineHarness sendSubCommand(@NotNull String parent, @Nullable String group, @NotNull String sub, @NotNull SlashOption... options) {
        this.awaitCommandRegistered(parent, Duration.ofSeconds(10));
        this.gatewayClient.emit(this.dispatchFactory.slashSubCommand(parent, group, sub, options));
        return this;
    }

    /**
     * Waits for the named user (right-click) command to register, then pushes a simulated invocation
     * targeting the given user.
     *
     * @param name the command name
     * @param targetUserId the targeted user id
     * @return this harness
     */
    public @NotNull OfflineHarness sendUserCommand(@NotNull String name, long targetUserId) {
        this.awaitCommandRegistered(name, Duration.ofSeconds(10));
        this.gatewayClient.emit(this.dispatchFactory.userCommand(name, targetUserId));
        return this;
    }

    /**
     * Waits for the named message (right-click) command to register, then pushes a simulated invocation
     * targeting the given message.
     *
     * @param name the command name
     * @param targetMessageId the targeted message id
     * @return this harness
     */
    public @NotNull OfflineHarness sendMessageCommand(@NotNull String name, long targetMessageId) {
        this.awaitCommandRegistered(name, Duration.ofSeconds(10));
        this.gatewayClient.emit(this.dispatchFactory.messageCommand(name, targetMessageId));
        return this;
    }

    /**
     * Blocks until the component dispatcher has registered a route for the given custom id, so a
     * simulated interaction targeting an {@link dev.simplified.discordapi.listener.Component @Component}
     * route (in particular an eternal, cache-miss interaction) will resolve.
     *
     * @param customId the route custom id
     * @param timeout the maximum time to wait
     * @return this harness
     */
    public @NotNull OfflineHarness awaitComponentRoute(@NotNull String customId, @NotNull Duration timeout) {
        awaitTrue(
            () -> this.bot.getComponentDispatcher() != null && this.bot.getComponentDispatcher().findRoute(customId).isPresent(),
            timeout,
            "component route '" + customId + "' not registered"
        );
        return this;
    }

    /**
     * Blocks until a response for the given message id is present in the response cache, so a simulated
     * component interaction on it will resolve (avoids racing the reply's cache write).
     *
     * @param messageId the cached message id
     * @param timeout the maximum time to wait
     * @return this harness
     */
    public @NotNull OfflineHarness awaitResponseCached(long messageId, @NotNull Duration timeout) {
        Snowflake snowflake = Snowflake.of(messageId);
        awaitTrue(
            () -> Boolean.TRUE.equals(this.bot.getResponseLocator().findByMessage(snowflake).hasElement().block(Duration.ofSeconds(1))),
            timeout,
            "response for message " + messageId + " not cached"
        );
        return this;
    }

    /**
     * Waits for the reply on the given message to be cached, then pushes a simulated button click for the
     * given custom id into the live dispatch pipeline.
     *
     * @param messageId the cached message id the button lives on
     * @param customId the button custom id
     * @return this harness
     */
    public @NotNull OfflineHarness clickButton(long messageId, @NotNull String customId) {
        this.awaitResponseCached(messageId, Duration.ofSeconds(10));
        this.gatewayClient.emit(this.dispatchFactory.button(messageId, customId));
        return this;
    }

    /**
     * Waits for the reply on the given message to be cached, then pushes a simulated string select menu
     * interaction carrying the given selected values into the live dispatch pipeline.
     *
     * @param messageId the cached message id the select menu lives on
     * @param customId the select menu custom id
     * @param values the selected option values
     * @return this harness
     */
    public @NotNull OfflineHarness clickSelectMenu(long messageId, @NotNull String customId, @NotNull String... values) {
        this.awaitResponseCached(messageId, Duration.ofSeconds(10));
        this.gatewayClient.emit(this.dispatchFactory.selectMenu(messageId, customId, values));
        return this;
    }

    /**
     * Pushes a simulated modal submit for a modal that was opened from the given cached message.
     *
     * @param messageId the cached message the modal belongs to
     * @param modalCustomId the modal's custom id
     * @param inputId the text input's custom id
     * @param value the submitted value
     * @return this harness
     */
    public @NotNull OfflineHarness submitModal(long messageId, @NotNull String modalCustomId, @NotNull String inputId, @NotNull String value) {
        this.gatewayClient.emit(this.dispatchFactory.modalSubmit(messageId, modalCustomId, inputId, value));
        return this;
    }

    /**
     * Waits for the reply on the given message to be cached, then pushes a bot-authored
     * {@code MESSAGE_CREATE} for it, driving the response's {@code onCreate} handler.
     *
     * @param messageId the cached message id
     * @return this harness
     */
    public @NotNull OfflineHarness emitMessageCreate(long messageId) {
        this.awaitResponseCached(messageId, Duration.ofSeconds(10));
        this.gatewayClient.emit(this.dispatchFactory.messageCreate(messageId));
        return this;
    }

    public @NotNull DispatchFactory dispatches() {
        return this.dispatchFactory;
    }

    public @NotNull HarnessBot bot() {
        return this.bot;
    }

    public @NotNull List<RecordedRequest> requests() {
        return this.server.requests();
    }

    /**
     * Waits until at least one recorded request matches the predicate, then returns it.
     *
     * @param predicate the request matcher
     * @param timeout the maximum time to wait
     * @return the first matching recorded request
     */
    public @NotNull RecordedRequest awaitRequest(@NotNull Predicate<RecordedRequest> predicate, @NotNull Duration timeout) {
        awaitTrue(() -> this.server.requests().stream().anyMatch(predicate), timeout, "no request matched within " + timeout);
        return this.server.requests().stream().filter(predicate).reduce((first, second) -> second).orElseThrow();
    }

    /**
     * Waits (default 10s) for the most recent request matching the predicate.
     *
     * @param predicate the request matcher
     * @return the matching recorded request
     */
    public @NotNull RecordedRequest awaitRequest(@NotNull Predicate<RecordedRequest> predicate) {
        return this.awaitRequest(predicate, Duration.ofSeconds(10));
    }

    /**
     * Waits (default 10s) for the most recent interaction reply edit ({@code PATCH .../messages/@original}).
     *
     * @return the matching recorded request
     */
    public @NotNull RecordedRequest awaitInteractionReply() {
        return this.awaitRequest(request -> request.method().equals("PATCH") && request.path().endsWith("/messages/@original"));
    }

    /**
     * Waits (default 10s) for the most recent interaction callback ({@code POST .../callback}).
     *
     * @return the matching recorded request
     */
    public @NotNull RecordedRequest awaitInteractionCallback() {
        return this.awaitRequest(request -> request.method().equals("POST") && request.path().endsWith("/callback"));
    }

    /**
     * Counts the interaction callbacks ({@code POST .../callback}) whose path contains the given token,
     * i.e. how many times that interaction was acknowledged. Discord permits exactly one.
     *
     * @param tokenContains a substring of the interaction token/path to match
     * @return the number of acknowledgment callbacks recorded for it
     */
    public long callbackCount(@NotNull String tokenContains) {
        return this.server.requests().stream()
            .filter(request -> request.method().equals("POST")
                && request.path().contains(tokenContains)
                && request.path().endsWith("/callback"))
            .count();
    }

    private boolean gatewayConnected() {
        try {
            this.bot.getGateway();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void awaitTrue(@NotNull java.util.function.BooleanSupplier condition, @NotNull Duration timeout, @NotNull String message) {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean())
                return;

            try {
                Thread.sleep(25);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting: " + message, interrupted);
            }
        }

        throw new IllegalStateException(message + "; recorded requests=" + this.server.requests());
    }

    @Override
    public void close() {
        this.gatewayClient.close(false).subscribe();
        this.server.stop();
    }

}
