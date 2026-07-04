package dev.simplified.discordapi.harness.gateway;

import dev.simplified.discordapi.harness.HarnessConfig;
import dev.simplified.discordapi.harness.gateway.dispatch.ComponentDispatches;
import dev.simplified.discordapi.harness.gateway.dispatch.InteractionDispatches;
import dev.simplified.discordapi.harness.gateway.dispatch.LifecycleDispatches;
import dev.simplified.discordapi.harness.gateway.dispatch.MessageDispatches;
import dev.simplified.discordapi.harness.json.HarnessEntities;
import discord4j.discordjson.json.gateway.Dispatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Builds Discord4J {@link Dispatch} objects for the offline harness. A thin facade over the grouped builders
 * in {@code gateway.dispatch} - lifecycle, command interactions, message-attached component interactions, and
 * message events - so callers reach every dispatch through one entry point while the construction lives in
 * focused, per-kind classes.
 * <p>
 * The dispatches are built through the discord-json immutable builders (the same types Discord4J deserializes
 * the wire into) rather than hand-written JSON, so a renamed or restructured field breaks the compile instead
 * of silently producing a bad dispatch. Each group logs the shape it produced at TRACE.
 */
public final class DispatchFactory {

    private final LifecycleDispatches lifecycle;
    private final InteractionDispatches interactions;
    private final ComponentDispatches components;
    private final MessageDispatches messages;

    public DispatchFactory(@NotNull HarnessConfig config, @NotNull HarnessEntities entities) {
        this.lifecycle = new LifecycleDispatches(config, entities);
        this.interactions = new InteractionDispatches(config, entities);
        this.components = new ComponentDispatches(config, entities);
        this.messages = new MessageDispatches(entities);
    }

    /**
     * Builds the startup handshake replayed on connect: a {@code READY} followed by a connected state change
     * (the latter completes login and emits {@code ConnectEvent}).
     *
     * @param botId the bot user / application id
     * @return the ordered handshake dispatches
     */
    public @NotNull List<Dispatch> handshake(long botId) {
        return this.lifecycle.handshake(botId);
    }

    /**
     * Builds a top-level slash command {@code INTERACTION_CREATE} carrying the given resolved options.
     *
     * @param name the slash command name
     * @param options the resolved top-level options, if any
     * @return the interaction-create dispatch
     */
    public @NotNull Dispatch slashCommand(@NotNull String name, @NotNull SlashOption... options) {
        return this.interactions.slashCommand(name, options);
    }

    /**
     * Builds a nested subcommand {@code INTERACTION_CREATE} ({@code parent [group] sub options}).
     *
     * @param parent the parent command name
     * @param group the subcommand group name, or {@code null}/blank for a bare subcommand
     * @param sub the subcommand name
     * @param options the resolved leaf options, if any
     * @return the interaction-create dispatch
     */
    public @NotNull Dispatch slashSubCommand(@NotNull String parent, @Nullable String group, @NotNull String sub, @NotNull SlashOption... options) {
        return this.interactions.slashSubCommand(parent, group, sub, options);
    }

    /**
     * Builds a right-click user command {@code INTERACTION_CREATE} targeting the given user.
     *
     * @param name the command name
     * @param targetUserId the id of the targeted user
     * @return the user command dispatch
     */
    public @NotNull Dispatch userCommand(@NotNull String name, long targetUserId) {
        return this.interactions.userCommand(name, targetUserId);
    }

    /**
     * Builds a right-click message command {@code INTERACTION_CREATE} targeting the given message.
     *
     * @param name the command name
     * @param targetMessageId the id of the targeted message
     * @return the message command dispatch
     */
    public @NotNull Dispatch messageCommand(@NotNull String name, long targetMessageId) {
        return this.interactions.messageCommand(name, targetMessageId);
    }

    /**
     * Builds a button click {@code INTERACTION_CREATE} on the given cached message.
     *
     * @param messageId the id of the cached message the button lives on
     * @param customId the button's custom id
     * @return the component interaction dispatch
     */
    public @NotNull Dispatch button(long messageId, @NotNull String customId) {
        return this.components.button(messageId, customId);
    }

    /**
     * Builds a string select menu {@code INTERACTION_CREATE} on the given cached message.
     *
     * @param messageId the id of the cached message the select menu lives on
     * @param customId the select menu's custom id
     * @param values the selected option values
     * @return the component interaction dispatch
     */
    public @NotNull Dispatch selectMenu(long messageId, @NotNull String customId, @NotNull String... values) {
        return this.components.selectMenu(messageId, customId, values);
    }

    /**
     * Builds a modal submit {@code INTERACTION_CREATE} carrying one text input value.
     *
     * @param messageId the id of the cached message the modal belongs to
     * @param modalCustomId the modal's custom id
     * @param inputId the text input's custom id
     * @param value the submitted text value
     * @return the modal submit dispatch
     */
    public @NotNull Dispatch modalSubmit(long messageId, @NotNull String modalCustomId, @NotNull String inputId, @NotNull String value) {
        return this.components.modalSubmit(messageId, modalCustomId, inputId, value);
    }

    /**
     * Builds a modal submit {@code INTERACTION_CREATE} carrying a radio group value and a checkbox value.
     *
     * @param messageId the id of the cached message the modal belongs to
     * @param modalCustomId the modal's custom id
     * @param radioId the radio group's custom id
     * @param radioValue the selected radio option value
     * @param checkboxId the checkbox's custom id
     * @param checkboxChecked whether the checkbox was checked
     * @return the modal submit dispatch
     */
    public @NotNull Dispatch modalSubmitValues(long messageId, @NotNull String modalCustomId, @NotNull String radioId, @NotNull String radioValue, @NotNull String checkboxId, boolean checkboxChecked) {
        return this.components.modalSubmitValues(messageId, modalCustomId, radioId, radioValue, checkboxId, checkboxChecked);
    }

    /**
     * Builds a bot-authored {@code MESSAGE_CREATE} for the given message, used to drive a cached response's
     * {@code onCreate} handler.
     *
     * @param messageId the message id (must match a cached response)
     * @return the message-create dispatch
     */
    public @NotNull Dispatch messageCreate(long messageId) {
        return this.messages.messageCreate(messageId);
    }

}
