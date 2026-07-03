package dev.simplified.discordapi.harness;

import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.handler.CommandHandler;
import dev.simplified.discordapi.handler.DiscordConfig;
import dev.simplified.discordapi.harness.data.TestIds;
import dev.simplified.discordapi.harness.gateway.DispatchFactory;
import dev.simplified.discordapi.harness.gateway.FakeGatewayClient;
import dev.simplified.discordapi.harness.rest.LocalDiscordServer;
import dev.simplified.discordapi.harness.rest.RecordedRequest;
import dev.simplified.util.Logging;
import discord4j.common.ReactorResources;
import discord4j.common.util.Snowflake;
import discord4j.discordjson.json.gateway.Dispatch;
import org.jetbrains.annotations.NotNull;
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

    private final LocalDiscordServer server;
    private final FakeGatewayClient gatewayClient;
    private final DispatchFactory dispatchFactory = new DispatchFactory();
    private final HarnessBot bot;

    public OfflineHarness() {
        this.server = new LocalDiscordServer(TestIds.BOT_ID).start();

        List<Dispatch> handshake = this.dispatchFactory.handshake(TestIds.BOT_ID);
        this.gatewayClient = new FakeGatewayClient(handshake, 1);

        ReactorResources plaintextRest = ReactorResources.builder()
            .httpClient(HttpClient.create().compress(true).followRedirect(true)) // no .secure() -> plaintext http
            .build();

        DiscordConfig config = DiscordConfig.builder()
            .withToken(TestIds.TOKEN)
            .withMainGuildId(TestIds.GUILD_ID)
            .withCommands("dev.simplified.discordapi.harness.command")
            .withApiBaseUrl(this.server.baseUrl())
            .withRestReactorResources(plaintextRest)
            .withGatewayClientFactory(options -> this.gatewayClient)
            .withLogLevel(Logging.Level.INFO)
            .build();

        this.bot = new HarnessBot(config);
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
        seedContextMenuCommandIds();
        return this;
    }

    /**
     * Workaround for main-code bug H4: {@code CommandHandler.buildCommandRequests} only registers slash
     * commands, so user/message (context-menu) command ids are never mapped and their invocations cannot
     * route. Seed the id map with the same deterministic ids the mock assigns, so the dispatch pathway is
     * testable. Idempotent with the real fix (same ids), so it stays correct once H4 is addressed.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void seedContextMenuCommandIds() {
        CommandHandler handler = this.bot.getCommandHandler();

        try {
            java.lang.reflect.Field field = CommandHandler.class.getDeclaredField("commandIds");
            field.setAccessible(true);
            java.util.Map ids = (java.util.Map) field.get(handler);

            java.util.stream.Stream.concat(handler.getUserCommands().stream(), handler.getMessageCommands().stream())
                .forEach((Object command) -> ids.put(
                    command.getClass(),
                    TestIds.commandId(((DiscordCommand<?>) command).getStructure().name())
                ));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to seed context-menu command ids (harness workaround for H4)", exception);
        }
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
        long commandId = TestIds.commandId(name);
        awaitTrue(() -> !this.bot.getCommandHandler().getCommandsById(commandId).isEmpty(), timeout, "command '" + name + "' not registered");
        return this;
    }

    /**
     * Waits for the named slash command to be registered, then pushes a simulated {@code type 2}
     * interaction for it into the live dispatch pipeline.
     *
     * @param name the slash command name
     * @return this harness
     */
    public @NotNull OfflineHarness sendSlashCommand(@NotNull String name) {
        this.awaitCommandRegistered(name, Duration.ofSeconds(10));
        this.gatewayClient.emit(this.dispatchFactory.slashCommand(name));
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
