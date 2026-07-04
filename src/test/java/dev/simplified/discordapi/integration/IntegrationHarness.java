package dev.simplified.discordapi.integration;

import dev.simplified.discordapi.handler.DiscordConfig;
import dev.simplified.discordapi.handler.response.EternalResponseRepository;
import dev.simplified.discordapi.handler.response.InMemoryEternalResponseRepository;
import dev.simplified.discordapi.harness.HarnessConfig;
import dev.simplified.discordapi.harness.OfflineHarness;
import dev.simplified.discordapi.harness.gateway.DispatchFactory;
import dev.simplified.discordapi.harness.gateway.FakeGatewayClient;
import dev.simplified.discordapi.harness.gateway.SlashOption;
import dev.simplified.discordapi.harness.rest.RecordedRequest;
import dev.simplified.util.Logging;
import discord4j.common.ReactorResources;
import discord4j.common.util.Snowflake;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * The consumer side of the offline harness: connects this project's {@link HarnessBot} to an
 * {@link OfflineHarness} server and drives it. It builds the bot's {@link DiscordConfig} pointed at the
 * server's REST mock and fake gateway, boots the bot on a daemon thread, then exposes a DSL to push simulated
 * gateway events and assert on the captured REST traffic.
 * <p>
 * Tests boot it, wait until connected, then drive dispatches and assert. The DSL narrates each driven event
 * at INFO and each wait/teardown at DEBUG; raise the {@code dev.simplified.discordapi.integration} logger to
 * DEBUG or TRACE for more detail.
 */
@Log4j2
public final class IntegrationHarness implements AutoCloseable {

    private final HarnessConfig config;
    private final OfflineHarness server;
    private final HarnessBot bot;

    /** Boots with the standard harness identity ({@code HarnessConfig.builder().build()}). */
    public IntegrationHarness() {
        this(HarnessConfig.builder().build());
    }

    /**
     * Boots with the given harness identity and a fresh in-memory eternal store.
     *
     * @param config the harness identity to use
     */
    public IntegrationHarness(@NotNull HarnessConfig config) {
        this(config, InMemoryEternalResponseRepository.of());
    }

    /**
     * Boots with the given harness identity and an explicit {@link EternalResponseRepository}, connecting the
     * bot to a fresh {@link OfflineHarness} server backed by that store. Pass the same store to two successive
     * harnesses to simulate a reboot: the first creates an eternal message, the second (with a fresh hot tier)
     * re-hydrates it from the shared cold store.
     *
     * @param config the harness identity to use
     * @param eternalRepository the eternal cold store to back this run
     */
    public IntegrationHarness(@NotNull HarnessConfig config, @NotNull EternalResponseRepository eternalRepository) {
        this.config = config;
        this.server = new OfflineHarness(config, eternalRepository);

        ReactorResources plaintextRest = ReactorResources.builder()
            .httpClient(HttpClient.create().compress(true).followRedirect(true)) // no .secure() -> plaintext http
            .build();

        DiscordConfig discordConfig = DiscordConfig.builder()
            .withToken(config.getToken())
            .withMainGuildId(config.getGuildId())
            .withCommands("dev.simplified.discordapi.integration.command")
            .withApiBaseUrl(this.server.baseUrl())
            .withRestReactorResources(plaintextRest)
            .withGatewayClientFactory(options -> this.server.gateway())
            .withEternalRepository(this.server.eternalRepository())
            .withLogLevel(Logging.Level.INFO)
            .build();

        this.bot = new HarnessBot(discordConfig);
        log.info("Integration harness constructed against REST base {}", this.server.baseUrl());
    }

    /** The harness identity backing this run (ids, token, command-id scheme). */
    public @NotNull HarnessConfig config() {
        return this.config;
    }

    /** The eternal cold store backing this run; share it across two harnesses to simulate a reboot. */
    public @NotNull EternalResponseRepository eternalRepository() {
        return this.server.eternalRepository();
    }

    /**
     * Boots the bot and blocks until the gateway reports connected or the timeout elapses.
     *
     * @param timeout the maximum time to wait for connection
     * @return this harness
     */
    public @NotNull IntegrationHarness boot(@NotNull Duration timeout) {
        log.info("Booting bot, waiting up to {} for the gateway to connect", timeout);
        this.bot.bootAsync();
        this.server.await(this::gatewayConnected, timeout, "gateway did not connect");
        log.info("Gateway connected");
        return this;
    }

    /** The fake in-JVM gateway; call {@code emit(...)} to push a raw dispatch into the live pipeline. */
    public @NotNull FakeGatewayClient gateway() {
        return this.server.gateway();
    }

    /** The dispatch factory that builds simulated gateway events for this identity. */
    public @NotNull DispatchFactory dispatches() {
        return this.server.dispatches();
    }

    /** The booted bot under test. */
    public @NotNull HarnessBot bot() {
        return this.bot;
    }

    /**
     * Blocks until the named command has been assigned its Discord id (i.e. bulk-overwrite completed and
     * the id mapping was populated), so a simulated interaction can route to it.
     *
     * @param name the command name
     * @param timeout the maximum time to wait
     * @return this harness
     */
    public @NotNull IntegrationHarness awaitCommandRegistered(@NotNull String name, @NotNull Duration timeout) {
        long commandId = this.config.commandId(name);
        this.server.await(() -> !this.bot.getCommandHandler().getCommandsById(commandId).isEmpty(), timeout, "command '" + name + "' not registered");
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
    public @NotNull IntegrationHarness sendSlashCommand(@NotNull String name, @NotNull SlashOption... options) {
        log.info("-> slash command /{}{}", name, describe(options));
        this.awaitCommandRegistered(name, Duration.ofSeconds(10));
        this.server.gateway().emit(this.server.dispatches().slashCommand(name, options));
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
    public @NotNull IntegrationHarness sendSubCommand(@NotNull String parent, @NotNull String sub, @NotNull SlashOption... options) {
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
    public @NotNull IntegrationHarness sendSubCommand(@NotNull String parent, @Nullable String group, @NotNull String sub, @NotNull SlashOption... options) {
        String path = parent + (group == null ? "" : " " + group) + " " + sub;
        log.info("-> slash subcommand /{}{}", path, describe(options));
        this.awaitCommandRegistered(parent, Duration.ofSeconds(10));
        this.server.gateway().emit(this.server.dispatches().slashSubCommand(parent, group, sub, options));
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
    public @NotNull IntegrationHarness sendUserCommand(@NotNull String name, long targetUserId) {
        log.info("-> user command '{}' on user {}", name, targetUserId);
        this.awaitCommandRegistered(name, Duration.ofSeconds(10));
        this.server.gateway().emit(this.server.dispatches().userCommand(name, targetUserId));
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
    public @NotNull IntegrationHarness sendMessageCommand(@NotNull String name, long targetMessageId) {
        log.info("-> message command '{}' on message {}", name, targetMessageId);
        this.awaitCommandRegistered(name, Duration.ofSeconds(10));
        this.server.gateway().emit(this.server.dispatches().messageCommand(name, targetMessageId));
        return this;
    }

    /**
     * Blocks until the component dispatcher has registered a route for the given custom id, so a
     * simulated interaction targeting a {@link dev.simplified.discordapi.listener.Component @Component}
     * route (in particular an eternal, cache-miss interaction) will resolve.
     *
     * @param customId the route custom id
     * @param timeout the maximum time to wait
     * @return this harness
     */
    public @NotNull IntegrationHarness awaitComponentRoute(@NotNull String customId, @NotNull Duration timeout) {
        this.server.await(
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
    public @NotNull IntegrationHarness awaitResponseCached(long messageId, @NotNull Duration timeout) {
        Snowflake snowflake = Snowflake.of(messageId);
        this.server.await(
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
    public @NotNull IntegrationHarness clickButton(long messageId, @NotNull String customId) {
        log.info("-> click button '{}' on message {}", customId, messageId);
        this.awaitResponseCached(messageId, Duration.ofSeconds(10));
        this.server.gateway().emit(this.server.dispatches().button(messageId, customId));
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
    public @NotNull IntegrationHarness clickSelectMenu(long messageId, @NotNull String customId, @NotNull String... values) {
        log.info("-> select '{}' on message {} values={}", customId, messageId, Arrays.toString(values));
        this.awaitResponseCached(messageId, Duration.ofSeconds(10));
        this.server.gateway().emit(this.server.dispatches().selectMenu(messageId, customId, values));
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
    public @NotNull IntegrationHarness submitModal(long messageId, @NotNull String modalCustomId, @NotNull String inputId, @NotNull String value) {
        log.info("-> submit modal '{}' input '{}'='{}' on message {}", modalCustomId, inputId, value, messageId);
        this.server.gateway().emit(this.server.dispatches().modalSubmit(messageId, modalCustomId, inputId, value));
        return this;
    }

    /**
     * Waits for the reply on the given message to be cached, then pushes a bot-authored
     * {@code MESSAGE_CREATE} for it, driving the response's {@code onCreate} handler.
     *
     * @param messageId the cached message id
     * @return this harness
     */
    public @NotNull IntegrationHarness emitMessageCreate(long messageId) {
        log.info("-> MESSAGE_CREATE for message {}", messageId);
        this.awaitResponseCached(messageId, Duration.ofSeconds(10));
        this.server.gateway().emit(this.server.dispatches().messageCreate(messageId));
        return this;
    }

    /** All requests the REST mock has recorded, in order. */
    public @NotNull List<RecordedRequest> requests() {
        return this.server.requests();
    }

    /**
     * Waits until at least one recorded request matches the predicate, then returns the most recent match.
     *
     * @param predicate the request matcher
     * @param timeout the maximum time to wait
     * @return the last matching recorded request
     */
    public @NotNull RecordedRequest awaitRequest(@NotNull Predicate<RecordedRequest> predicate, @NotNull Duration timeout) {
        return this.server.awaitRequest(predicate, timeout);
    }

    /**
     * Waits (default 10s) for the most recent request matching the predicate.
     *
     * @param predicate the request matcher
     * @return the matching recorded request
     */
    public @NotNull RecordedRequest awaitRequest(@NotNull Predicate<RecordedRequest> predicate) {
        return this.server.awaitRequest(predicate);
    }

    /**
     * Waits (default 10s) for the most recent interaction reply edit ({@code PATCH .../messages/@original}).
     *
     * @return the matching recorded request
     */
    public @NotNull RecordedRequest awaitInteractionReply() {
        return this.server.awaitInteractionReply();
    }

    /**
     * Waits (default 10s) for the most recent interaction callback ({@code POST .../callback}).
     *
     * @return the matching recorded request
     */
    public @NotNull RecordedRequest awaitInteractionCallback() {
        return this.server.awaitInteractionCallback();
    }

    /**
     * Counts the interaction callbacks ({@code POST .../callback}) whose path contains the given token,
     * i.e. how many times that interaction was acknowledged. Discord permits exactly one.
     *
     * @param tokenContains a substring of the interaction token/path to match
     * @return the number of acknowledgment callbacks recorded for it
     */
    public long callbackCount(@NotNull String tokenContains) {
        return this.server.callbackCount(tokenContains);
    }

    private boolean gatewayConnected() {
        try {
            this.bot.getGateway();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Renders slash options for a log line, or an empty string when there are none. */
    private static @NotNull String describe(@NotNull SlashOption... options) {
        return options.length == 0 ? "" : " " + Arrays.toString(options);
    }

    @Override
    public void close() {
        log.debug("Closing integration harness");
        this.server.close();
    }

}
