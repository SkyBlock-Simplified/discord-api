package dev.simplified.discordapi.harness;

import dev.simplified.discordapi.handler.response.EternalResponseRepository;
import dev.simplified.discordapi.handler.response.InMemoryEternalResponseRepository;
import dev.simplified.discordapi.harness.gateway.DispatchFactory;
import dev.simplified.discordapi.harness.gateway.FakeGatewayClient;
import dev.simplified.discordapi.harness.rest.LocalDiscordServer;
import dev.simplified.discordapi.harness.rest.RecordedRequest;
import discord4j.discordjson.json.gateway.Dispatch;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * The server side of the offline harness: a localhost REST mock, a fake in-JVM gateway, and the deterministic
 * identity ({@link HarnessConfig}) plus durable eternal store the two share. It stands in for Discord itself
 * and knows nothing about any bot - a consumer wires its own {@code DiscordBot} to {@link #baseUrl()} and
 * {@link #gateway()}, drives dispatches built by {@link #dispatches()}, and asserts on the captured REST
 * traffic exposed here.
 * <p>
 * Each instance is one fresh deployment: server, gateway, and dispatch factory are recreated per harness,
 * while the pluggable {@link EternalResponseRepository} can be shared across two harnesses to simulate a
 * reboot (the second, with a fresh hot tier, re-hydrates from the same cold store).
 * <p>
 * Narrates construction and teardown through Log4j2; raise the {@code dev.simplified.discordapi.harness}
 * logger to DEBUG or TRACE to see the REST and dispatch detail.
 */
@Log4j2
public final class OfflineHarness implements AutoCloseable {

    private final HarnessConfig config;
    private final EternalResponseRepository eternalRepository;
    private final LocalDiscordServer server;
    private final FakeGatewayClient gatewayClient;
    private final DispatchFactory dispatchFactory;

    /** Boots with the standard harness identity ({@code HarnessConfig.builder().build()}). */
    public OfflineHarness() {
        this(HarnessConfig.builder().build());
    }

    /**
     * Boots with the given harness identity and a fresh in-memory eternal store.
     *
     * @param config the harness identity to use
     */
    public OfflineHarness(@NotNull HarnessConfig config) {
        this(config, InMemoryEternalResponseRepository.of());
    }

    /**
     * Boots with the given harness identity and an explicit {@link EternalResponseRepository}, threading them
     * through the REST mock and the dispatch factory. Pass the same store to two successive harnesses to
     * simulate a reboot: the first creates an eternal message, the second (with a fresh hot tier) re-hydrates
     * it from the shared cold store.
     *
     * @param config the harness identity to use
     * @param eternalRepository the eternal cold store to back this run
     */
    public OfflineHarness(@NotNull HarnessConfig config, @NotNull EternalResponseRepository eternalRepository) {
        this.config = config;
        this.eternalRepository = eternalRepository;
        this.dispatchFactory = new DispatchFactory(config);
        this.server = new LocalDiscordServer(config).start();

        List<Dispatch> handshake = this.dispatchFactory.handshake(config.getBotId());
        this.gatewayClient = new FakeGatewayClient(handshake, 1);

        log.info("Offline harness server constructed: botId={} guildId={} REST base {}", config.getBotId(), config.getGuildId(), this.server.baseUrl());
    }

    /** The harness identity backing this run (ids, token, command-id scheme). */
    public @NotNull HarnessConfig config() {
        return this.config;
    }

    /** The eternal cold store backing this run; share it across two harnesses to simulate a reboot. */
    public @NotNull EternalResponseRepository eternalRepository() {
        return this.eternalRepository;
    }

    /** The base url of the localhost REST mock; point a bot's {@code DiscordConfig.withApiBaseUrl} at it. */
    public @NotNull String baseUrl() {
        return this.server.baseUrl();
    }

    /** The fake in-JVM gateway; wire it via {@code withGatewayClientFactory} and push dispatches with {@code emit}. */
    public @NotNull FakeGatewayClient gateway() {
        return this.gatewayClient;
    }

    /** The dispatch factory that builds simulated gateway events for this identity. */
    public @NotNull DispatchFactory dispatches() {
        return this.dispatchFactory;
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
        await(() -> this.server.requests().stream().anyMatch(predicate), timeout, "no request matched within " + timeout);
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

    /**
     * Blocks the calling test thread until the condition holds, polling every 25ms until the timeout elapses.
     *
     * <p>
     * Polls rather than awaiting a signal because the awaited conditions - gateway connected, command
     * registered, response cached, request matched - are external state mutated on the bot's reactive and
     * netty threads with nothing to latch onto, so a bounded poll is the pragmatic harness primitive.
     *
     * @param condition the condition to await
     * @param timeout the maximum time to wait
     * @param message the failure description used in the log and the thrown exception
     * @throws IllegalStateException if the timeout elapses, or the thread is interrupted, before the condition holds
     */
    @SuppressWarnings("BusyWait")
    public void await(@NotNull BooleanSupplier condition, @NotNull Duration timeout, @NotNull String message) {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean())
                return;

            try {
                Thread.sleep(25);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting: {}", message);
                throw new IllegalStateException("Interrupted while waiting: " + message, interrupted);
            }
        }

        log.error("Timed out after {} waiting: {}; recorded requests={}", timeout, message, this.server.requests());
        throw new IllegalStateException(message + "; recorded requests=" + this.server.requests());
    }

    @Override
    public void close() {
        log.debug("Closing harness");
        this.gatewayClient.close(false).subscribe();
        this.server.stop();
    }

}
