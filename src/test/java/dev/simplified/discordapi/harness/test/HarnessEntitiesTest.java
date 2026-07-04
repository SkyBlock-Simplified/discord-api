package dev.simplified.discordapi.harness.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplified.discordapi.harness.HarnessConfig;
import dev.simplified.discordapi.harness.json.HarnessEntities;
import discord4j.discordjson.json.ApplicationInfoData;
import discord4j.discordjson.json.ChannelData;
import discord4j.discordjson.json.EmojiData;
import discord4j.discordjson.json.GatewayData;
import discord4j.discordjson.json.GuildUpdateData;
import discord4j.discordjson.json.MessageData;
import discord4j.discordjson.json.UserData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirms every {@link HarnessEntities} body round-trips: the JSON it emits deserializes back into the
 * discord-json immutable Discord4J expects, with the identifying fields intact. Catches a malformed or
 * mistyped entity builder that the wire format would otherwise reject at runtime.
 */
class HarnessEntitiesTest {

    private final HarnessConfig config = HarnessConfig.builder().build();
    private final HarnessEntities entities = new HarnessEntities(this.config);
    private final ObjectMapper mapper = this.entities.mapper();

    @Test
    void self_user_round_trips() throws Exception {
        UserData user = this.mapper.readValue(this.entities.userJson(), UserData.class);
        assertEquals(this.config.getBotId(), user.id().asLong());
        assertEquals("TestBot", user.username());
    }

    @Test
    void gateway_round_trips() throws Exception {
        GatewayData gateway = this.mapper.readValue(this.entities.gatewayJson(), GatewayData.class);
        assertEquals(this.config.getGatewayUrl(), gateway.url());
    }

    @Test
    void gateway_bot_carries_session_limits() throws Exception {
        GatewayData gateway = this.mapper.readValue(this.entities.gatewayBotJson(), GatewayData.class);
        assertEquals(1, gateway.shards().toOptional().orElseThrow());
        assertEquals(1000, gateway.sessionStartLimit().toOptional().orElseThrow().total());
    }

    @Test
    void application_info_round_trips() throws Exception {
        ApplicationInfoData application = this.mapper.readValue(this.entities.applicationInfoJson(), ApplicationInfoData.class);
        assertEquals(this.config.getBotId(), application.id().asLong());
        assertEquals(this.config.getVerifyKey(), application.verifyKey());
    }

    @Test
    void guild_round_trips() throws Exception {
        GuildUpdateData guild = this.mapper.readValue(this.entities.guildJson(), GuildUpdateData.class);
        assertEquals(this.config.getGuildId(), guild.id().asLong());
        assertEquals("Harness Guild", guild.name());
    }

    @Test
    void reply_message_round_trips() throws Exception {
        MessageData message = this.mapper.readValue(this.entities.messageJson(), MessageData.class);
        assertEquals(this.config.getReplyMessageId(), message.id().asLong());
    }

    @Test
    void empty_emojis_is_an_empty_item_list() throws Exception {
        JsonNode node = this.mapper.readTree(this.entities.emptyEmojisJson());
        assertTrue(node.get("items").isArray() && node.get("items").isEmpty());
    }

    @Test
    void created_emoji_round_trips() throws Exception {
        EmojiData emoji = this.mapper.readValue(this.entities.emojiJson(), EmojiData.class);
        assertEquals("harness", emoji.name().orElseThrow());
        assertTrue(emoji.id().isPresent());
    }

    @Test
    void channel_round_trips() throws Exception {
        ChannelData channel = this.mapper.readValue(this.entities.channelJson(), ChannelData.class);
        assertEquals(this.config.getChannelId(), channel.id().asLong());
        assertEquals(0, channel.type());
    }

    @Test
    void reaction_users_is_an_empty_array() throws Exception {
        JsonNode node = this.mapper.readTree(this.entities.reactionUsersJson());
        assertTrue(node.isArray() && node.isEmpty());
    }

    @Test
    void typed_entities_serialize_and_re_read() throws Exception {
        UserData bot = reread(this.entities.botUser(), UserData.class);
        assertEquals(this.config.getBotId(), bot.id().asLong());

        UserData actor = reread(this.entities.actorUser(), UserData.class);
        assertEquals(this.config.getUserId(), actor.id().asLong());

        MessageData interactionMessage = reread(this.entities.interactionMessage(4242L), MessageData.class);
        assertEquals(4242L, interactionMessage.id().asLong());
    }

    private <T> T reread(Object entity, Class<T> type) throws Exception {
        return this.mapper.readValue(this.mapper.writeValueAsString(entity), type);
    }

}
