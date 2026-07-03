package dev.simplified.discordapi.harness.gateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplified.discordapi.harness.data.TestIds;
import discord4j.common.JacksonResources;
import discord4j.discordjson.json.gateway.Dispatch;
import discord4j.gateway.json.GatewayPayload;
import discord4j.gateway.retry.GatewayStateChange;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds Discord4J {@link Dispatch} objects from gateway JSON envelopes via the same Jackson mapper the
 * gateway uses, so tests can supply plain JSON instead of hand-assembling discord-json immutables.
 */
public final class DispatchFactory {

    private final ObjectMapper mapper = JacksonResources.create().getObjectMapper();

    /**
     * Deserializes a full gateway payload envelope ({@code op}/{@code t}/{@code s}/{@code d}) into its
     * concrete {@link Dispatch}.
     *
     * @param json the gateway payload json
     * @return the deserialized dispatch
     */
    public Dispatch fromJson(String json) {
        try {
            GatewayPayload<?> payload = this.mapper.readValue(json, new TypeReference<GatewayPayload<?>>() {});
            return (Dispatch) payload.getData();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize dispatch: " + json, exception);
        }
    }

    /**
     * Builds a minimal {@code READY} dispatch with an empty guild list.
     *
     * @param botId the bot user / application id
     * @return the ready dispatch
     */
    public Dispatch ready(long botId) {
        return this.fromJson(readyJson(botId));
    }

    /**
     * Builds the startup handshake replayed on connect: a {@code READY} followed by a connected state
     * change (the latter completes login and emits {@code ConnectEvent}).
     *
     * @param botId the bot user / application id
     * @return the ordered handshake dispatches
     */
    public List<Dispatch> handshake(long botId) {
        List<Dispatch> dispatches = new ArrayList<>();
        dispatches.add(this.ready(botId));
        dispatches.add(GatewayStateChange.connected());
        return dispatches;
    }

    /**
     * Builds an {@code INTERACTION_CREATE} (type 2, chat input) dispatch for a flat slash command with
     * no guild context, so the command runs as a private-channel interaction (skipping bot-permission
     * and channel resolution). The command id is derived from the name via {@link TestIds#commandId}.
     *
     * @param name the slash command name
     * @return the interaction-create dispatch
     */
    public Dispatch slashCommand(String name) {
        return this.fromJson(slashCommandJson(name, TestIds.commandId(name)));
    }

    /**
     * Builds an {@code INTERACTION_CREATE} (type 3, message component) dispatch for a button click on the
     * given cached message, referencing the button's explicit custom id. No guild context.
     *
     * @param messageId the id of the cached message the button lives on
     * @param customId the button's custom id
     * @return the component interaction dispatch
     */
    public Dispatch button(long messageId, String customId) {
        return this.fromJson(buttonJson(messageId, customId));
    }

    private static String buttonJson(long messageId, String customId) {
        return "{\"op\":0,\"s\":11,\"t\":\"INTERACTION_CREATE\",\"d\":{"
            + "\"id\":\"950000000000000011\","
            + "\"application_id\":\"" + TestIds.APPLICATION_ID + "\","
            + "\"type\":3,"
            + "\"token\":\"button-token-" + customId + "\","
            + "\"version\":1,"
            + "\"channel_id\":\"" + TestIds.CHANNEL_ID + "\","
            + "\"user\":{\"id\":\"" + TestIds.USER_ID + "\",\"username\":\"tester\",\"discriminator\":\"0001\",\"avatar\":null},"
            + "\"message\":" + messageObject(messageId) + ","
            + "\"data\":{\"custom_id\":\"" + customId + "\",\"component_type\":2}"
            + "}}";
    }

    /**
     * Builds an {@code INTERACTION_CREATE} (type 5, modal submit) dispatch carrying one text input value,
     * targeting the cached message the modal was opened from.
     *
     * @param messageId the id of the cached message the modal belongs to
     * @param modalCustomId the modal's custom id
     * @param inputId the text input's custom id
     * @param value the submitted text value
     * @return the modal submit dispatch
     */
    public Dispatch modalSubmit(long messageId, String modalCustomId, String inputId, String value) {
        return this.fromJson(modalSubmitJson(messageId, modalCustomId, inputId, value));
    }

    private static String modalSubmitJson(long messageId, String modalCustomId, String inputId, String value) {
        return "{\"op\":0,\"s\":12,\"t\":\"INTERACTION_CREATE\",\"d\":{"
            + "\"id\":\"950000000000000012\","
            + "\"application_id\":\"" + TestIds.APPLICATION_ID + "\","
            + "\"type\":5,"
            + "\"token\":\"modal-token-" + modalCustomId + "\","
            + "\"version\":1,"
            + "\"channel_id\":\"" + TestIds.CHANNEL_ID + "\","
            + "\"user\":{\"id\":\"" + TestIds.USER_ID + "\",\"username\":\"tester\",\"discriminator\":\"0001\",\"avatar\":null},"
            + "\"message\":" + messageObject(messageId) + ","
            + "\"data\":{\"custom_id\":\"" + modalCustomId + "\",\"components\":[{\"type\":1,\"components\":["
            + "{\"type\":4,\"custom_id\":\"" + inputId + "\",\"value\":\"" + value + "\"}]}]}"
            + "}}";
    }

    private static String messageObject(long messageId) {
        return "{\"id\":\"" + messageId + "\",\"channel_id\":\"" + TestIds.CHANNEL_ID + "\","
            + "\"author\":{\"id\":\"" + TestIds.APPLICATION_ID + "\",\"username\":\"TestBot\",\"discriminator\":\"0000\",\"avatar\":null,\"bot\":true},"
            + "\"content\":\"press it\",\"timestamp\":\"2020-01-01T00:00:00.000000+00:00\",\"edited_timestamp\":null,"
            + "\"tts\":false,\"mention_everyone\":false,\"mentions\":[],\"mention_roles\":[],\"attachments\":[],"
            + "\"embeds\":[],\"pinned\":false,\"type\":0}";
    }

    /**
     * Builds an {@code INTERACTION_CREATE} for a right-click user command (application command type 2),
     * targeting the given user.
     *
     * @param name the command name
     * @param targetUserId the id of the targeted user
     * @return the user command dispatch
     */
    public Dispatch userCommand(String name, long targetUserId) {
        return this.fromJson(contextMenuJson(name, 2, "user", targetUserId,
            "\"users\":{\"" + targetUserId + "\":" + userObject(targetUserId) + "}"));
    }

    /**
     * Builds an {@code INTERACTION_CREATE} for a right-click message command (application command type 3),
     * targeting the given message.
     *
     * @param name the command name
     * @param targetMessageId the id of the targeted message
     * @return the message command dispatch
     */
    public Dispatch messageCommand(String name, long targetMessageId) {
        return this.fromJson(contextMenuJson(name, 3, "message", targetMessageId,
            "\"messages\":{\"" + targetMessageId + "\":" + messageObject(targetMessageId) + "}"));
    }

    private static String contextMenuJson(String name, int commandType, String tokenPrefix, long targetId, String resolved) {
        return "{\"op\":0,\"s\":13,\"t\":\"INTERACTION_CREATE\",\"d\":{"
            + "\"id\":\"950000000000000013\","
            + "\"application_id\":\"" + TestIds.APPLICATION_ID + "\","
            + "\"type\":2,"
            + "\"token\":\"" + tokenPrefix + "-token-" + name + "\","
            + "\"version\":1,"
            + "\"channel_id\":\"" + TestIds.CHANNEL_ID + "\","
            + "\"user\":{\"id\":\"" + TestIds.USER_ID + "\",\"username\":\"tester\",\"discriminator\":\"0001\",\"avatar\":null},"
            + "\"data\":{\"id\":\"" + TestIds.commandId(name) + "\",\"name\":\"" + name + "\",\"type\":" + commandType + ","
            + "\"target_id\":\"" + targetId + "\",\"resolved\":{" + resolved + "}}"
            + "}}";
    }

    private static String userObject(long id) {
        return "{\"id\":\"" + id + "\",\"username\":\"target\",\"discriminator\":\"0002\",\"avatar\":null}";
    }

    private static String slashCommandJson(String name, long commandId) {
        return "{\"op\":0,\"s\":10,\"t\":\"INTERACTION_CREATE\",\"d\":{"
            + "\"id\":\"950000000000000010\","
            + "\"application_id\":\"" + TestIds.APPLICATION_ID + "\","
            + "\"type\":2,"
            + "\"token\":\"interaction-token-" + name + "\","
            + "\"version\":1,"
            + "\"channel_id\":\"" + TestIds.CHANNEL_ID + "\","
            + "\"user\":{\"id\":\"" + TestIds.USER_ID + "\",\"username\":\"tester\",\"discriminator\":\"0001\",\"avatar\":null},"
            + "\"data\":{\"id\":\"" + commandId + "\",\"name\":\"" + name + "\",\"type\":1}"
            + "}}";
    }

    private static String readyJson(long botId) {
        return "{\"op\":0,\"s\":1,\"t\":\"READY\",\"d\":{"
            + "\"v\":10,"
            + "\"user\":{\"id\":\"" + botId + "\",\"username\":\"TestBot\",\"discriminator\":\"0000\",\"avatar\":null,\"bot\":true},"
            + "\"guilds\":[],"
            + "\"private_channels\":[],"
            + "\"session_id\":\"fake-session\","
            + "\"resume_gateway_url\":\"ws://127.0.0.1/fake-gateway\","
            + "\"_trace\":[],"
            + "\"application\":{\"id\":\"" + botId + "\",\"flags\":0}"
            + "}}";
    }

}
