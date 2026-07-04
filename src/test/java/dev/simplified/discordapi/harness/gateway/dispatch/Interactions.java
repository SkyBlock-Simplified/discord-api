package dev.simplified.discordapi.harness.gateway.dispatch;

import dev.simplified.discordapi.harness.HarnessConfig;
import dev.simplified.discordapi.harness.json.HarnessEntities;
import discord4j.discordjson.json.ImmutableInteractionData;
import discord4j.discordjson.json.InteractionData;
import discord4j.discordjson.json.gateway.Dispatch;
import discord4j.discordjson.json.gateway.InteractionCreate;
import org.jetbrains.annotations.NotNull;

/**
 * Shared plumbing for the interaction-bearing dispatch groups: the {@code INTERACTION_CREATE} envelope and
 * the fields common to every simulated interaction.
 */
final class Interactions {

    private final HarnessConfig config;
    private final HarnessEntities entities;

    Interactions(@NotNull HarnessConfig config, @NotNull HarnessEntities entities) {
        this.config = config;
        this.entities = entities;
    }

    /** Wraps a completed interaction into an {@code INTERACTION_CREATE} dispatch. */
    @NotNull Dispatch interaction(@NotNull ImmutableInteractionData.Builder interaction) {
        return InteractionCreate.builder().interaction(interaction.build()).build();
    }

    /**
     * Seeds the fields common to every simulated interaction: ids, type, token, version, the channel, and the
     * (non-bot) acting user, with no guild context so it runs as a private-channel interaction.
     */
    @NotNull ImmutableInteractionData.Builder baseInteraction(long id, int type, @NotNull String token) {
        return InteractionData.builder()
            .id(id)
            .applicationId(this.config.getApplicationId())
            .type(type)
            .token(token)
            .version(1)
            .channelId(this.config.getChannelId())
            .user(this.entities.actorUser());
    }

}
