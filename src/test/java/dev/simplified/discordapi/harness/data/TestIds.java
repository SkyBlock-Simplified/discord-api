package dev.simplified.discordapi.harness.data;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Canonical snowflake ids and a structurally-valid fake bot token used across the offline harness.
 */
public final class TestIds {

    /** The bot's own user / application id. */
    public static final long BOT_ID = 111111111111111111L;
    /** The application id, aliased to {@link #BOT_ID}. */
    public static final long APPLICATION_ID = BOT_ID;
    /** The primary guild id (matches the debug commands' {@code guildId}). */
    public static final long GUILD_ID = 652148034448261150L;
    /** A text channel id within {@link #GUILD_ID}. */
    public static final long CHANNEL_ID = 222222222222222222L;
    /** A non-bot user id used as the actor for simulated interactions. */
    public static final long USER_ID = 333333333333333333L;
    /** The id the REST mock assigns to every reply/followup message (so component clicks can target it). */
    public static final long REPLY_MESSAGE_ID = 800000000000000001L;

    /**
     * A fake bot token whose first dot-separated segment base64-decodes to the decimal {@link #BOT_ID},
     * matching how Discord4J derives its self id via {@code TokenUtil.getSelfId}.
     */
    public static final String TOKEN = buildToken(BOT_ID);

    private TestIds() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Derives a stable application-command id from a command name, so the REST mock's bulk-overwrite
     * echo and a simulated interaction agree on the same id without coordination.
     *
     * @param name the command name
     * @return a deterministic snowflake-shaped id
     */
    public static long commandId(@NotNull String name) {
        return 700000000000000000L + (Math.abs(name.hashCode()) % 1000000000L);
    }

    private static String buildToken(long botId) {
        String head = Base64.getEncoder().encodeToString(Long.toString(botId).getBytes(StandardCharsets.UTF_8));
        return head + ".Gfake0." + "0123456789abcdefghijklmnopqrstuvwxyz";
    }

}
