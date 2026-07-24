package dev.simplified.discordapi.harness.json;

import dev.simplified.discordapi.harness.HarnessConfig;
import discord4j.common.JacksonResources;
import discord4j.core.object.entity.Guild;
import discord4j.discordjson.Id;
import discord4j.discordjson.json.ApplicationInfoData;
import discord4j.discordjson.json.ChannelData;
import discord4j.discordjson.json.EmojiData;
import discord4j.discordjson.json.GatewayData;
import discord4j.discordjson.json.GuildUpdateData;
import discord4j.discordjson.json.MessageData;
import discord4j.discordjson.json.PartialUserData;
import discord4j.discordjson.json.SessionStartLimitData;
import discord4j.discordjson.json.UserData;
import discord4j.discordjson.possible.Possible;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplified.annotations.RequiredArgsConstructor;
import dev.simplified.annotations.Log;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * The single home for the fake discord-json entities the harness fabricates: the typed immutables the
 * dispatch factory embeds in gateway events, and the JSON bodies the REST mock serves. Both are built through
 * Discord4J's own immutable builders and mapper, so they stay in step with Discord4J updates and serialize
 * exactly as the bot expects.
 * <p>
 * Consolidating them here keeps the user/message/entity shapes in one place rather than duplicated across the
 * dispatch factory and the REST server.
 */
@Log
@RequiredArgsConstructor
public final class HarnessEntities {

    // Empty list container returned by GET application emojis; not a Discord entity, so kept as a literal.
    private static final String EMPTY_EMOJIS_JSON = "{\"items\":[]}";

    // The id minted for a created application emoji.
    private static final long CREATED_EMOJI_ID = 900000000000000001L;

    // Discord4J's own mapper, so the discord-json immutables below serialize exactly as the bot expects.
    private final ObjectMapper mapper = JacksonResources.create().getObjectMapper();
    private final HarnessConfig config;

    /** Discord4J's mapper, shared so request-echo logic serializes with the same configuration. */
    public @NotNull ObjectMapper mapper() {
        return this.mapper;
    }

    // --- typed entities (embedded in dispatches) ---

    /** The bot's self user, reused as {@code GET /users/@me}, as the author of served messages, and in READY. */
    public @NotNull UserData botUser() {
        return user(this.config.getBotId(), "TestBot", "0000", true);
    }

    /** The non-bot user that acts as the invoker of simulated interactions. */
    public @NotNull UserData actorUser() {
        return user(this.config.getUserId(), "tester", "0001", false);
    }

    /** The partial user served as the application owner, so {@code DiscordReference#isDeveloper} resolves true for it. */
    public @NotNull PartialUserData developerUser() {
        return PartialUserData.builder()
            .id(this.config.getDeveloperUserId())
            .username("developer")
            .discriminator("0003")
            .build();
    }

    /**
     * A non-bot user targeted by a right-click user command.
     *
     * @param id the targeted user id
     * @return the target user
     */
    public @NotNull UserData targetUser(long id) {
        return user(id, "target", "0002", false);
    }

    /**
     * The fabricated message a component or modal interaction references (the cached message the component
     * lives on).
     *
     * @param messageId the message id (must match a cached response)
     * @return the interaction message
     */
    public @NotNull MessageData interactionMessage(long messageId) {
        return message(messageId, "press it");
    }

    // --- serialized JSON bodies (served by the REST mock) ---

    /** {@code GET /users/@me} - the bot's self user. */
    public @NotNull String userJson() {
        return this.write(this.botUser());
    }

    /** {@code GET /gateway} - the gateway url. */
    public @NotNull String gatewayJson() {
        return this.write(GatewayData.builder().url(this.config.getGatewayUrl()).build());
    }

    /** {@code GET /gateway/bot} - the gateway url with shard and session-start-limit metadata. */
    public @NotNull String gatewayBotJson() {
        return this.write(GatewayData.builder()
            .url(this.config.getGatewayUrl())
            .shards(1)
            .sessionStartLimit(SessionStartLimitData.builder()
                .total(1000)
                .remaining(1000)
                .resetAfter(0)
                .maxConcurrency(1)
                .build())
            .build());
    }

    /** {@code GET /oauth2/applications/@me} - the application info. */
    @SuppressWarnings("deprecation") // summary is a required field on ApplicationInfoData even though deprecated
    public @NotNull String applicationInfoJson() {
        return this.write(ApplicationInfoData.builder()
            .id(this.config.getBotId())
            .name("TestBot")
            .description("Offline harness application")
            .botPublic(true)
            .botRequireCodeGrant(false)
            .summary("")
            .verifyKey(this.config.getVerifyKey())
            .owner(this.developerUser())
            .build());
    }

    /** {@code GET /guilds/{guild}} - a minimal guild. */
    public @NotNull String guildJson() {
        return this.write(GuildUpdateData.builder()
            .id(this.config.getGuildId())
            .name("Harness Guild")
            .ownerId(this.config.getBotId())
            .afkTimeout(300)
            .verificationLevel(Guild.VerificationLevel.NONE.getValue())
            .defaultMessageNotifications(0)
            .explicitContentFilter(Guild.ContentFilterLevel.DISABLED.getValue())
            .mfaLevel(Guild.MfaLevel.NONE.getValue())
            .premiumTier(Guild.PremiumTier.NONE.getValue())
            .preferredLocale("en-US")
            .nsfwLevel(Guild.NsfwLevel.DEFAULT.getValue())
            .roles(List.of())
            .emojis(List.of())
            .build());
    }

    /** The message minted for every reply/followup ({@code POST}/{@code PATCH} message endpoints). */
    public @NotNull String messageJson() {
        return this.write(message(this.config.getReplyMessageId(), ""));
    }

    /** {@code GET /applications/{app}/emojis} - an empty emoji list container. */
    public @NotNull String emptyEmojisJson() {
        return EMPTY_EMOJIS_JSON;
    }

    /** {@code POST /applications/{app}/emojis} - a created application emoji. */
    public @NotNull String emojiJson() {
        return this.write(EmojiData.builder()
            .id(CREATED_EMOJI_ID)
            .name("harness")
            .build());
    }

    /** {@code GET /channels/{channel}} - a minimal text channel. */
    public @NotNull String channelJson() {
        return this.write(ChannelData.builder()
            .id(Id.of(this.config.getChannelId()))
            .type(0)
            .build());
    }

    /** {@code GET /channels/{channel}/messages/{message}/reactions/{emoji}} - the users who reacted (none). */
    public @NotNull String reactionUsersJson() {
        return "[]";
    }

    // --- shared builders ---

    private @NotNull MessageData message(long messageId, @NotNull String content) {
        return MessageData.builder()
            .id(messageId)
            .channelId(this.config.getChannelId())
            .author(this.botUser())
            .content(content)
            .timestamp(messageTimestamp())
            .editedTimestamp(Optional.empty())
            .tts(false)
            .mentionEveryone(false)
            .pinned(false)
            .type(0)
            .build();
    }

    @SuppressWarnings("deprecation") // discriminator is a required field on UserData even though deprecated
    private static @NotNull UserData user(long id, @NotNull String username, @NotNull String discriminator, boolean bot) {
        return UserData.builder()
            .id(id)
            .username(username)
            .discriminator(discriminator)
            .globalName(bot ? Optional.of(username) : Optional.empty())
            .avatar(Optional.empty())
            .bot(Possible.of(bot))
            .build();
    }

    /**
     * The current time as an ISO-8601 extended offset date-time (the format Discord sends for message
     * timestamps), so each fabricated message carries a real datetime rather than a fixed placeholder.
     */
    private static @NotNull String messageTimestamp() {
        return OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /** Serializes a discord-json immutable with Discord4J's mapper. */
    private @NotNull String write(@NotNull Object data) {
        try {
            return this.mapper.writeValueAsString(data);
        } catch (Exception exception) {
            log.error("Failed to serialize {} for a mock response", data.getClass().getSimpleName(), exception);
            throw new IllegalStateException("Failed to serialize " + data.getClass().getSimpleName(), exception);
        }
    }

}
