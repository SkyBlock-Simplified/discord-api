package dev.simplified.discordapi.harness.gateway.dispatch;

import dev.simplified.discordapi.harness.HarnessConfig;
import dev.simplified.discordapi.harness.gateway.SlashOption;
import dev.simplified.discordapi.harness.json.HarnessEntities;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandInteractionData;
import discord4j.discordjson.json.ApplicationCommandInteractionOptionData;
import discord4j.discordjson.json.ApplicationCommandInteractionResolvedData;
import discord4j.discordjson.json.ImmutableApplicationCommandInteractionData;
import discord4j.discordjson.json.ImmutableApplicationCommandInteractionOptionData;
import discord4j.discordjson.json.gateway.Dispatch;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;

/**
 * Builds the command {@code INTERACTION_CREATE} dispatches: chat-input slash commands (flat and nested) and
 * the right-click user/message context-menu commands.
 */
@Log4j2
public final class InteractionDispatches {

    private static final long SLASH_INTERACTION_ID = 950000000000000010L;
    private static final long CONTEXT_MENU_INTERACTION_ID = 950000000000000013L;

    private final HarnessConfig config;
    private final HarnessEntities entities;
    private final Interactions interactions;

    public InteractionDispatches(@NotNull HarnessConfig config, @NotNull HarnessEntities entities) {
        this.config = config;
        this.entities = entities;
        this.interactions = new Interactions(config, entities);
    }

    /**
     * Builds an {@code INTERACTION_CREATE} (type 2, chat input) dispatch for a top-level slash command with no
     * guild context, so the command runs as a private-channel interaction (skipping bot-permission and channel
     * resolution). The command id is derived from the name via {@link HarnessConfig#commandId}.
     *
     * @param name the slash command name
     * @param options the resolved top-level options, if any
     * @return the interaction-create dispatch
     */
    public @NotNull Dispatch slashCommand(@NotNull String name, @NotNull SlashOption... options) {
        log.trace("Building slash interaction '/{}' with {} option(s)", name, options.length);
        return this.interactions.interaction(
            this.interactions.baseInteraction(SLASH_INTERACTION_ID, 2, "interaction-token-" + name)
                .data(this.chatInputData(name, leafOptions(options)))
        );
    }

    /**
     * Builds an {@code INTERACTION_CREATE} (type 2, chat input) dispatch for a subcommand nested under a
     * parent - optionally within a subcommand group - matching the {@code parent [group] sub options} tree the
     * framework's {@code @Structure} resolution walks. The routing id is the parent's, since Discord registers
     * the whole tree under one top-level command.
     *
     * @param parent the parent command name
     * @param group the subcommand group name, or {@code null}/blank for a bare subcommand
     * @param sub the subcommand name
     * @param options the resolved leaf options, if any
     * @return the interaction-create dispatch
     */
    public @NotNull Dispatch slashSubCommand(@NotNull String parent, @Nullable String group, @NotNull String sub, @NotNull SlashOption... options) {
        log.trace("Building subcommand interaction: parent='{}' group='{}' sub='{}' with {} option(s)", parent, group, sub, options.length);
        ApplicationCommandInteractionOptionData subCommand = subCommandOption(sub, options);
        ApplicationCommandInteractionOptionData top = isPresent(group) ? groupOption(group, subCommand) : subCommand;

        return this.interactions.interaction(
            this.interactions.baseInteraction(SLASH_INTERACTION_ID, 2, subCommandToken(parent, group, sub))
                .data(this.chatInputData(parent, top))
        );
    }

    /**
     * Builds an {@code INTERACTION_CREATE} for a right-click user command (application command type 2),
     * targeting the given user.
     *
     * @param name the command name
     * @param targetUserId the id of the targeted user
     * @return the user command dispatch
     */
    public @NotNull Dispatch userCommand(@NotNull String name, long targetUserId) {
        log.trace("Building user context-menu interaction: name='{}' targetUser={}", name, targetUserId);
        return this.contextMenu(name, 2, "user-token-" + name, targetUserId,
            ApplicationCommandInteractionResolvedData.builder()
                .users(Map.of(Long.toString(targetUserId), this.entities.targetUser(targetUserId)))
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
    public @NotNull Dispatch messageCommand(@NotNull String name, long targetMessageId) {
        log.trace("Building message context-menu interaction: name='{}' targetMessage={}", name, targetMessageId);
        return this.contextMenu(name, 3, "message-token-" + name, targetMessageId,
            ApplicationCommandInteractionResolvedData.builder()
                .messages(Map.of(Long.toString(targetMessageId), this.entities.interactionMessage(targetMessageId)))
                .build());
    }

    private @NotNull Dispatch contextMenu(@NotNull String name, int commandType, @NotNull String token, long targetId, @NotNull ApplicationCommandInteractionResolvedData resolved) {
        return this.interactions.interaction(
            this.interactions.baseInteraction(CONTEXT_MENU_INTERACTION_ID, 2, token)
                .data(ApplicationCommandInteractionData.builder()
                    .id(Long.toString(this.config.commandId(name)))
                    .name(name)
                    .type(commandType)
                    .targetId(Long.toString(targetId))
                    .resolved(resolved)
                    .build())
        );
    }

    /**
     * Builds the chat-input command data whose top-level {@code name} and routing id both derive from
     * {@code routingName} - the command name for a flat command, or the parent name for a subcommand tree.
     */
    private @NotNull ApplicationCommandInteractionData chatInputData(@NotNull String routingName, @NotNull ApplicationCommandInteractionOptionData... options) {
        ImmutableApplicationCommandInteractionData.Builder data = ApplicationCommandInteractionData.builder()
            .id(Long.toString(this.config.commandId(routingName)))
            .name(routingName)
            .type(1);

        for (ApplicationCommandInteractionOptionData option : options)
            data.addOption(option);

        return data.build();
    }

    private static ApplicationCommandInteractionOptionData[] leafOptions(SlashOption... options) {
        return Arrays.stream(options)
            .map(InteractionDispatches::leafOption)
            .toArray(ApplicationCommandInteractionOptionData[]::new);
    }

    private static ApplicationCommandInteractionOptionData leafOption(SlashOption option) {
        return ApplicationCommandInteractionOptionData.builder()
            .name(option.name())
            .type(option.type().getValue())
            .value(option.value())
            .build();
    }

    private static ApplicationCommandInteractionOptionData subCommandOption(String name, SlashOption... options) {
        ImmutableApplicationCommandInteractionOptionData.Builder subCommand = ApplicationCommandInteractionOptionData.builder()
            .name(name)
            .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue());

        for (SlashOption option : options)
            subCommand.addOption(leafOption(option));

        return subCommand.build();
    }

    private static ApplicationCommandInteractionOptionData groupOption(String name, ApplicationCommandInteractionOptionData subCommand) {
        return ApplicationCommandInteractionOptionData.builder()
            .name(name)
            .type(ApplicationCommandOption.Type.SUB_COMMAND_GROUP.getValue())
            .addOption(subCommand)
            .build();
    }

    private static String subCommandToken(String parent, @Nullable String group, String sub) {
        StringBuilder token = new StringBuilder("interaction-token-").append(parent).append('-');

        if (isPresent(group))
            token.append(group).append('-');

        return token.append(sub).toString();
    }

    private static boolean isPresent(@Nullable String value) {
        return value != null && !value.isBlank();
    }

}
