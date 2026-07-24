package dev.simplified.discordapi.harness.gateway.dispatch;

import dev.simplified.discordapi.harness.HarnessConfig;
import dev.simplified.discordapi.harness.json.HarnessEntities;
import discord4j.discordjson.json.ApplicationCommandInteractionData;
import discord4j.discordjson.json.ComponentData;
import discord4j.discordjson.json.gateway.Dispatch;
import dev.simplified.annotations.Log;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Builds the message-attached {@code INTERACTION_CREATE} dispatches: button clicks, string select menus, and
 * modal submits (single text input, or a radio group plus checkbox).
 */
@Log
public final class ComponentDispatches {

    private static final long COMPONENT_INTERACTION_ID = 950000000000000011L;
    private static final long MODAL_INTERACTION_ID = 950000000000000012L;

    private final HarnessEntities entities;
    private final Interactions interactions;

    public ComponentDispatches(@NotNull HarnessConfig config, @NotNull HarnessEntities entities) {
        this.entities = entities;
        this.interactions = new Interactions(config, entities);
    }

    /**
     * Builds an {@code INTERACTION_CREATE} (type 3, message component) dispatch for a button click on the
     * given cached message, referencing the button's explicit custom id. No guild context.
     *
     * @param messageId the id of the cached message the button lives on
     * @param customId the button's custom id
     * @return the component interaction dispatch
     */
    public @NotNull Dispatch button(long messageId, @NotNull String customId) {
        log.trace("Building button interaction: customId='{}' message={}", customId, messageId);
        return this.interactions.interaction(
            this.interactions.baseInteraction(COMPONENT_INTERACTION_ID, 3, "button-token-" + customId)
                .message(this.entities.interactionMessage(messageId))
                .data(ApplicationCommandInteractionData.builder()
                    .customId(customId)
                    .componentType(2)
                    .build())
        );
    }

    /**
     * Builds an {@code INTERACTION_CREATE} (type 3, message component) dispatch for a string select menu on
     * the given cached message, carrying the chosen values. No guild context.
     *
     * @param messageId the id of the cached message the select menu lives on
     * @param customId the select menu's custom id
     * @param values the selected option values
     * @return the component interaction dispatch
     */
    public @NotNull Dispatch selectMenu(long messageId, @NotNull String customId, @NotNull String... values) {
        log.trace("Building select-menu interaction: customId='{}' message={} values={}", customId, messageId, Arrays.toString(values));
        return this.interactions.interaction(
            this.interactions.baseInteraction(COMPONENT_INTERACTION_ID, 3, "select-token-" + customId)
                .message(this.entities.interactionMessage(messageId))
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
    public @NotNull Dispatch modalSubmit(long messageId, @NotNull String modalCustomId, @NotNull String inputId, @NotNull String value) {
        return this.modalSubmit(messageId, modalCustomId, List.of(actionRow(textInput(inputId, value))));
    }

    /**
     * Builds an {@code INTERACTION_CREATE} (type 5, modal submit) dispatch carrying a radio group value (type
     * 21, {@code values}) and a checkbox value (type 23, {@code value}), so tests can verify that
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
    public @NotNull Dispatch modalSubmitValues(long messageId, @NotNull String modalCustomId, @NotNull String radioId, @NotNull String radioValue, @NotNull String checkboxId, boolean checkboxChecked) {
        return this.modalSubmit(messageId, modalCustomId, List.of(
            actionRow(radio(radioId, radioValue)),
            actionRow(checkbox(checkboxId, checkboxChecked))
        ));
    }

    /**
     * Builds an {@code INTERACTION_CREATE} (type 5, modal submit) dispatch carrying the given already-built
     * component rows, so a fluent multi-component submit can assemble any mix of inner components.
     *
     * @param messageId the id of the cached message the modal belongs to
     * @param modalCustomId the modal's custom id
     * @param components the top-level component rows carrying the submitted values
     * @return the modal submit dispatch
     */
    public @NotNull Dispatch modalSubmit(long messageId, @NotNull String modalCustomId, @NotNull List<ComponentData> components) {
        log.trace("Building modal submit: modal='{}' message={} with {} component row(s)", modalCustomId, messageId, components.size());
        return this.interactions.interaction(
            this.interactions.baseInteraction(MODAL_INTERACTION_ID, 5, "modal-token-" + modalCustomId)
                .message(this.entities.interactionMessage(messageId))
                .data(ApplicationCommandInteractionData.builder()
                    .customId(modalCustomId)
                    .components(components)
                    .build())
        );
    }

    /**
     * Builds an action-row-wrapped text input value row (component type 4).
     *
     * @param inputId the text input's custom id
     * @param value the submitted text value
     * @return the row carrying the text input
     */
    public static @NotNull ComponentData textRow(@NotNull String inputId, @NotNull String value) {
        return actionRow(textInput(inputId, value));
    }

    /**
     * Builds an action-row-wrapped radio group value row (component type 21, {@code values}).
     *
     * @param radioId the radio group's custom id
     * @param value the selected radio option value
     * @return the row carrying the radio group
     */
    public static @NotNull ComponentData radioRow(@NotNull String radioId, @NotNull String value) {
        return actionRow(radio(radioId, value));
    }

    /**
     * Builds an action-row-wrapped string select value row (component type 3, {@code values}), used for a
     * select menu hosted inside a modal.
     *
     * @param selectId the select menu's custom id
     * @param values the selected option values
     * @return the row carrying the select menu
     */
    public static @NotNull ComponentData selectRow(@NotNull String selectId, @NotNull String... values) {
        return actionRow(stringSelect(selectId, values));
    }

    /**
     * Builds an action-row-wrapped single checkbox value row (component type 23, {@code value}).
     *
     * @param checkboxId the checkbox's custom id
     * @param checked whether the checkbox was checked
     * @return the row carrying the checkbox
     */
    public static @NotNull ComponentData checkboxRow(@NotNull String checkboxId, boolean checked) {
        return actionRow(checkbox(checkboxId, checked));
    }

    /**
     * Builds an action-row-wrapped checkbox group value row (component type 22, {@code values}), used for the
     * multi-select filter group hosted inside a modal.
     *
     * @param groupId the checkbox group's custom id
     * @param values the selected option values
     * @return the row carrying the checkbox group
     */
    public static @NotNull ComponentData checkboxGroupRow(@NotNull String groupId, @NotNull String... values) {
        return actionRow(checkboxGroup(groupId, values));
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

    private static ComponentData stringSelect(String customId, String... values) {
        return ComponentData.builder().type(3).customId(customId).values(values).build();
    }

    private static ComponentData checkbox(String customId, boolean checked) {
        return ComponentData.builder().type(23).customId(customId).value(Boolean.toString(checked)).build();
    }

    private static ComponentData checkboxGroup(String customId, String... values) {
        return ComponentData.builder().type(22).customId(customId).values(values).build();
    }

}
