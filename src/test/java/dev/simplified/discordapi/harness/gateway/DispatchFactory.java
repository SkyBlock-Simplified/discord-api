package dev.simplified.discordapi.harness.gateway;

import dev.simplified.discordapi.harness.HarnessConfig;
import discord4j.discordjson.json.ApplicationCommandInteractionData;
import discord4j.discordjson.json.ApplicationCommandInteractionResolvedData;
import discord4j.discordjson.json.ComponentData;
import discord4j.discordjson.json.ImmutableInteractionData;
import discord4j.discordjson.json.InteractionData;
import discord4j.discordjson.json.MessageData;
import discord4j.discordjson.json.PartialApplicationInfoData;
import discord4j.discordjson.json.UserData;
import discord4j.discordjson.json.gateway.Dispatch;
import discord4j.discordjson.json.gateway.InteractionCreate;
import discord4j.discordjson.json.gateway.MessageCreate;
import discord4j.discordjson.json.gateway.Ready;
import discord4j.discordjson.possible.Possible;
import discord4j.gateway.retry.GatewayStateChange;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds Discord4J {@link Dispatch} objects for the offline harness directly from the discord-json
 * immutable builders (the same types Discord4J deserializes the gateway wire into), rather than from
 * hand-written JSON. Building through the typed builders keeps the payloads in step with Discord4J
 * updates - a renamed or restructured field breaks the compile instead of silently producing a bad
 * dispatch.
 */
public final class DispatchFactory {

    private static final long SLASH_INTERACTION_ID = 950000000000000010L;
    private static final long COMPONENT_INTERACTION_ID = 950000000000000011L;
    private static final long MODAL_INTERACTION_ID = 950000000000000012L;
    private static final long CONTEXT_MENU_INTERACTION_ID = 950000000000000013L;
    private static final String MESSAGE_TIMESTAMP = "2020-01-01T00:00:00.000000+00:00";
    private static final String GATEWAY_URL = "ws://127.0.0.1/fake-gateway";

    private final HarnessConfig config;

    public DispatchFactory(@NotNull HarnessConfig config) {
        this.config = config;
    }

    /**
     * Builds a minimal {@code READY} dispatch with an empty guild list.
     *
     * @param botId the bot user / application id
     * @return the ready dispatch
     */
    public Dispatch ready(long botId) {
        return Ready.builder()
            .v(10)
            .user(botUser(botId))
            .sessionId("fake-session")
            .resumeGatewayUrl(GATEWAY_URL)
            .application(PartialApplicationInfoData.builder().id(Long.toString(botId)).build())
            .build();
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
     * and channel resolution). The command id is derived from the name via {@link HarnessConfig#commandId}.
     *
     * @param name the slash command name
     * @return the interaction-create dispatch
     */
    public Dispatch slashCommand(String name) {
        return interaction(
            baseInteraction(SLASH_INTERACTION_ID, 2, "interaction-token-" + name)
                .data(ApplicationCommandInteractionData.builder()
                    .id(Long.toString(this.config.commandId(name)))
                    .name(name)
                    .type(1)
                    .build())
        );
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
        return interaction(
            baseInteraction(COMPONENT_INTERACTION_ID, 3, "button-token-" + customId)
                .message(messageData(messageId))
                .data(ApplicationCommandInteractionData.builder()
                    .customId(customId)
                    .componentType(2)
                    .build())
        );
    }

    /**
     * Builds an {@code INTERACTION_CREATE} (type 3, message component) dispatch for a string select menu
     * on the given cached message, carrying the chosen values. No guild context.
     *
     * @param messageId the id of the cached message the select menu lives on
     * @param customId the select menu's custom id
     * @param values the selected option values
     * @return the component interaction dispatch
     */
    public Dispatch selectMenu(long messageId, String customId, String... values) {
        return interaction(
            baseInteraction(COMPONENT_INTERACTION_ID, 3, "select-token-" + customId)
                .message(messageData(messageId))
                .data(ApplicationCommandInteractionData.builder()
                    .customId(customId)
                    .componentType(3)
                    .values(values)
                    .build())
        );
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
        return modalSubmit(messageId, modalCustomId, List.of(actionRow(textInput(inputId, value))));
    }

    /**
     * Builds an {@code INTERACTION_CREATE} (type 5, modal submit) dispatch carrying a radio group value
     * (type 21, {@code values}) and a checkbox value (type 23, {@code value}), so tests can verify that
     * non-text-input components fold their submitted state through the modal.
     *
     * @param messageId the id of the cached message the modal belongs to
     * @param modalCustomId the modal's custom id
     * @param radioId the radio group's custom id
     * @param radioValue the selected radio option value
     * @param checkboxId the checkbox's custom id
     * @param checkboxChecked whether the checkbox was checked
     * @return the modal submit dispatch
     */
    public Dispatch modalSubmitValues(long messageId, String modalCustomId, String radioId, String radioValue, String checkboxId, boolean checkboxChecked) {
        return modalSubmit(messageId, modalCustomId, List.of(
            actionRow(radio(radioId, radioValue)),
            actionRow(checkbox(checkboxId, checkboxChecked))
        ));
    }

    private Dispatch modalSubmit(long messageId, String modalCustomId, List<ComponentData> components) {
        return interaction(
            baseInteraction(MODAL_INTERACTION_ID, 5, "modal-token-" + modalCustomId)
                .message(messageData(messageId))
                .data(ApplicationCommandInteractionData.builder()
                    .customId(modalCustomId)
                    .components(components)
                    .build())
        );
    }

    /**
     * Builds a {@code MESSAGE_CREATE} dispatch for a bot-authored message with the given id, used to drive
     * a cached response's {@code onCreate} handler.
     *
     * @param messageId the message id (must match a cached response)
     * @return the message-create dispatch
     */
    public Dispatch messageCreate(long messageId) {
        return MessageCreate.builder().message(messageData(messageId)).build();
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
        return contextMenu(name, 2, "user-token-" + name, targetUserId,
            ApplicationCommandInteractionResolvedData.builder()
                .users(Map.of(Long.toString(targetUserId), targetUser(targetUserId)))
                .build());
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
        return contextMenu(name, 3, "message-token-" + name, targetMessageId,
            ApplicationCommandInteractionResolvedData.builder()
                .messages(Map.of(Long.toString(targetMessageId), messageData(targetMessageId)))
                .build());
    }

    private Dispatch contextMenu(String name, int commandType, String token, long targetId, ApplicationCommandInteractionResolvedData resolved) {
        return interaction(
            baseInteraction(CONTEXT_MENU_INTERACTION_ID, 2, token)
                .data(ApplicationCommandInteractionData.builder()
                    .id(Long.toString(this.config.commandId(name)))
                    .name(name)
                    .type(commandType)
                    .targetId(Long.toString(targetId))
                    .resolved(resolved)
                    .build())
        );
    }

    // --- shared builders ---

    private static Dispatch interaction(ImmutableInteractionData.Builder interaction) {
        return InteractionCreate.builder().interaction(interaction.build()).build();
    }

    /**
     * Seeds the fields common to every simulated interaction: ids, type, token, version, the channel,
     * and the (non-bot) acting user, with no guild context so it runs as a private-channel interaction.
     */
    private ImmutableInteractionData.Builder baseInteraction(long id, int type, String token) {
        return InteractionData.builder()
            .id(id)
            .applicationId(this.config.getApplicationId())
            .type(type)
            .token(token)
            .version(1)
            .channelId(this.config.getChannelId())
            .user(this.actorUser());
    }

    private static ComponentData actionRow(ComponentData child) {
        return ComponentData.builder().type(1).addComponent(child).build();
    }

    private static ComponentData textInput(String customId, String value) {
        return ComponentData.builder().type(4).customId(customId).value(value).build();
    }

    private static ComponentData radio(String customId, String value) {
        return ComponentData.builder().type(21).customId(customId).values(value).build();
    }

    private static ComponentData checkbox(String customId, boolean checked) {
        return ComponentData.builder().type(23).customId(customId).value(Boolean.toString(checked)).build();
    }

    private UserData actorUser() {
        return user(this.config.getUserId(), "tester", "0001", false);
    }

    private static UserData targetUser(long id) {
        return user(id, "target", "0002", false);
    }

    private static UserData botUser(long botId) {
        return user(botId, "TestBot", "0000", true);
    }

    @SuppressWarnings("deprecation") // discriminator is a required field on UserData even though deprecated
    private static UserData user(long id, String username, String discriminator, boolean bot) {
        return UserData.builder()
            .id(id)
            .username(username)
            .discriminator(discriminator)
            .globalName(Optional.empty())
            .avatar(Optional.empty())
            .bot(Possible.of(bot))
            .build();
    }

    private MessageData messageData(long messageId) {
        return MessageData.builder()
            .id(messageId)
            .channelId(this.config.getChannelId())
            .author(botUser(this.config.getApplicationId()))
            .content("press it")
            .timestamp(MESSAGE_TIMESTAMP)
            .editedTimestamp(Optional.empty())
            .tts(false)
            .mentionEveryone(false)
            .pinned(false)
            .type(0)
            .build();
    }

}
