package dev.simplified.discordapi.harness;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Standalone configuration for the offline harness: the canonical snowflake ids, a structurally-valid fake
 * bot token, the fake gateway endpoint, and the deterministic command-id scheme shared by the REST mock and
 * the simulated dispatches.
 *
 * <p>
 * Built via {@link #builder()}; every value has a sensible default, so {@code HarnessConfig.builder().build()}
 * yields the standard harness identity. The instance is passed to {@link OfflineHarness},
 * {@link dev.simplified.discordapi.harness.rest.LocalDiscordServer LocalDiscordServer}, and
 * {@link dev.simplified.discordapi.harness.gateway.DispatchFactory DispatchFactory} at construction, so a test
 * can vary the identity by supplying a customized config. This is deliberately NOT a
 * {@code DiscordConfig} - it is harness-only metadata.
 */
@Getter
public final class HarnessConfig {

    /** The bot's user id. */
    private final long botId;

    /** The application id (defaults to the bot id). */
    private final long applicationId;

    /** The primary guild id. */
    private final long guildId;

    /** The text channel id used for interactions and messages. */
    private final long channelId;

    /** The non-bot user id used as the actor for simulated interactions. */
    private final long userId;

    /** The user id served as the application owner, so {@code isDeveloper} resolves true for it. */
    private final long developerUserId;

    /** The id the REST mock assigns to every reply/followup message. */
    private final long replyMessageId;

    /** The fake bot token whose first segment base64-decodes to the bot id (so {@code TokenUtil} agrees). */
    private final @NotNull String token;

    /** The host of the fake gateway websocket url. */
    private final @NotNull String gatewayHost;

    /** The port of the fake gateway websocket url. */
    private final int gatewayPort;

    /** The path of the fake gateway websocket url. */
    private final @NotNull String gatewayPath;

    /** The application verify key served in the application info response. */
    private final @NotNull String verifyKey;

    /** Whether the REST mock prints every request it receives (defaults to the {@code harness.debug} property). */
    private final boolean debug;

    private HarnessConfig(@NotNull Builder builder) {
        this.botId = builder.botId;
        this.applicationId = builder.applicationId != null ? builder.applicationId : builder.botId;
        this.guildId = builder.guildId;
        this.channelId = builder.channelId;
        this.userId = builder.userId;
        this.developerUserId = builder.developerUserId;
        this.replyMessageId = builder.replyMessageId;
        this.token = builder.token != null ? builder.token : buildToken(builder.botId);
        this.gatewayHost = builder.gatewayHost;
        this.gatewayPort = builder.gatewayPort;
        this.gatewayPath = builder.gatewayPath;
        this.verifyKey = builder.verifyKey;
        this.debug = builder.debug;
    }

    /**
     * Derives a stable application-command id from a command name, so the REST mock's bulk-overwrite echo and
     * a simulated interaction agree on the same id without coordination.
     *
     * @param name the command name
     * @return a deterministic snowflake-shaped id
     */
    public long commandId(@NotNull String name) {
        return 700000000000000000L + (Math.abs(name.hashCode()) % 1000000000L);
    }

    /**
     * The fake gateway websocket url, combining {@link #getGatewayHost() host}, {@link #getGatewayPort() port},
     * and {@link #getGatewayPath() path}. Served as the {@code /gateway} url and replayed as the resume url;
     * the in-JVM fake gateway never actually dials it.
     *
     * @return the {@code ws://host:port/path} gateway url
     */
    public @NotNull String getGatewayUrl() {
        return "ws://" + this.gatewayHost + ":" + this.gatewayPort + "/" + this.gatewayPath;
    }

    /** Returns a new builder pre-populated with the standard harness defaults. */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    private static @NotNull String buildToken(long botId) {
        String head = Base64.getEncoder().encodeToString(Long.toString(botId).getBytes(StandardCharsets.UTF_8));
        return head + ".Gfake0." + "0123456789abcdefghijklmnopqrstuvwxyz";
    }

    /** Builder for {@link HarnessConfig}, carrying the default harness identity. */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder {

        private long botId = 111111111111111111L;
        private Long applicationId = null;
        private long guildId = 208023865127862272L;
        private long channelId = 222222222222222222L;
        private long userId = 333333333333333333L;
        private long developerUserId = 444444444444444444L;
        private long replyMessageId = 800000000000000001L;
        private String token = null;
        private String gatewayHost = "127.0.0.1";
        private int gatewayPort = 443;
        private String gatewayPath = "fake-gateway";
        private String verifyKey = "0".repeat(64);
        private boolean debug = Boolean.getBoolean("harness.debug");

        /** Sets the bot user id (the application id and token default from it). */
        public @NotNull Builder withBotId(long botId) {
            this.botId = botId;
            return this;
        }

        /** Overrides the application id (defaults to the bot id). */
        public @NotNull Builder withApplicationId(long applicationId) {
            this.applicationId = applicationId;
            return this;
        }

        /** Sets the primary guild id. */
        public @NotNull Builder withGuildId(long guildId) {
            this.guildId = guildId;
            return this;
        }

        /** Sets the text channel id. */
        public @NotNull Builder withChannelId(long channelId) {
            this.channelId = channelId;
            return this;
        }

        /** Sets the actor user id. */
        public @NotNull Builder withUserId(long userId) {
            this.userId = userId;
            return this;
        }

        /** Sets the application-owner (developer) user id served in the application info. */
        public @NotNull Builder withDeveloperUserId(long developerUserId) {
            this.developerUserId = developerUserId;
            return this;
        }

        /** Sets the reply/followup message id minted by the REST mock. */
        public @NotNull Builder withReplyMessageId(long replyMessageId) {
            this.replyMessageId = replyMessageId;
            return this;
        }

        /** Overrides the bot token (defaults to a structurally-valid token derived from the bot id). */
        public @NotNull Builder withToken(@NotNull String token) {
            this.token = token;
            return this;
        }

        /** Sets the fake gateway host. */
        public @NotNull Builder withGatewayHost(@NotNull String gatewayHost) {
            this.gatewayHost = gatewayHost;
            return this;
        }

        /** Sets the fake gateway port. */
        public @NotNull Builder withGatewayPort(int gatewayPort) {
            this.gatewayPort = gatewayPort;
            return this;
        }

        /** Sets the fake gateway path. */
        public @NotNull Builder withGatewayPath(@NotNull String gatewayPath) {
            this.gatewayPath = gatewayPath;
            return this;
        }

        /** Sets the application verify key. */
        public @NotNull Builder withVerifyKey(@NotNull String verifyKey) {
            this.verifyKey = verifyKey;
            return this;
        }

        /** Overrides request-logging (defaults to the {@code harness.debug} system property). */
        public @NotNull Builder withDebug(boolean debug) {
            this.debug = debug;
            return this;
        }

        /** Builds the config, defaulting the application id to the bot id and the token to a derived value. */
        public @NotNull HarnessConfig build() {
            return new HarnessConfig(this);
        }

    }

}
