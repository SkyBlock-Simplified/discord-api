package dev.simplified.discordapi.harness;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Standalone configuration for the offline harness: the canonical snowflake ids, a structurally-valid fake
 * bot token, and the deterministic command-id scheme shared by the REST mock and the simulated dispatches.
 *
 * <p>
 * Built via {@link #builder()}; every value has a sensible default, so {@code HarnessConfig.builder().build()}
 * yields the standard harness identity. The instance is passed to {@link OfflineHarness},
 * {@link dev.simplified.discordapi.harness.rest.LocalDiscordServer LocalDiscordServer}, and
 * {@link dev.simplified.discordapi.harness.gateway.DispatchFactory DispatchFactory} at construction, so a test
 * can vary the identity by supplying a customized config. This is deliberately NOT a
 * {@link dev.simplified.discordapi.handler.DiscordConfig DiscordConfig} - it is harness-only metadata.
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

    /** The id the REST mock assigns to every reply/followup message. */
    private final long replyMessageId;

    /** The fake bot token whose first segment base64-decodes to the bot id (so {@code TokenUtil} agrees). */
    private final @NotNull String token;

    private HarnessConfig(long botId, long applicationId, long guildId, long channelId, long userId, long replyMessageId, @NotNull String token) {
        this.botId = botId;
        this.applicationId = applicationId;
        this.guildId = guildId;
        this.channelId = channelId;
        this.userId = userId;
        this.replyMessageId = replyMessageId;
        this.token = token;
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

    /** Returns a new builder pre-populated with the standard harness defaults. */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    private static @NotNull String buildToken(long botId) {
        String head = Base64.getEncoder().encodeToString(Long.toString(botId).getBytes(StandardCharsets.UTF_8));
        return head + ".Gfake0." + "0123456789abcdefghijklmnopqrstuvwxyz";
    }

    /** Builder for {@link HarnessConfig}, carrying the default harness identity. */
    public static final class Builder {

        private long botId = 111111111111111111L;
        private Long applicationId = null;
        private long guildId = 652148034448261150L;
        private long channelId = 222222222222222222L;
        private long userId = 333333333333333333L;
        private long replyMessageId = 800000000000000001L;
        private String token = null;

        private Builder() {}

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

        /** Builds the config, defaulting the application id to the bot id and the token to a derived value. */
        public @NotNull HarnessConfig build() {
            long resolvedApplicationId = this.applicationId != null ? this.applicationId : this.botId;
            String resolvedToken = this.token != null ? this.token : buildToken(this.botId);
            return new HarnessConfig(this.botId, resolvedApplicationId, this.guildId, this.channelId, this.userId, this.replyMessageId, resolvedToken);
        }

    }

}
