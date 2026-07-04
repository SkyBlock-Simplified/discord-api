package dev.simplified.discordapi.harness.gateway.dispatch;

import dev.simplified.discordapi.harness.HarnessConfig;
import dev.simplified.discordapi.harness.json.HarnessEntities;
import discord4j.discordjson.json.PartialApplicationInfoData;
import discord4j.discordjson.json.gateway.Dispatch;
import discord4j.discordjson.json.gateway.Ready;
import discord4j.gateway.retry.GatewayStateChange;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the gateway lifecycle dispatches: the {@code READY} and the startup handshake replayed on connect.
 */
@Log4j2
public final class LifecycleDispatches {

    private final HarnessConfig config;
    private final HarnessEntities entities;

    public LifecycleDispatches(@NotNull HarnessConfig config, @NotNull HarnessEntities entities) {
        this.config = config;
        this.entities = entities;
    }

    /**
     * Builds a minimal {@code READY} dispatch with an empty guild list.
     *
     * @param botId the bot user / application id
     * @return the ready dispatch
     */
    public @NotNull Dispatch ready(long botId) {
        log.trace("Building READY for bot {}", botId);
        return Ready.builder()
            .v(10)
            .user(this.entities.botUser())
            .sessionId("fake-session")
            .resumeGatewayUrl(this.config.getGatewayUrl())
            .application(PartialApplicationInfoData.builder().id(Long.toString(botId)).build())
            .build();
    }

    /**
     * Builds the startup handshake replayed on connect: a {@code READY} followed by a connected state change
     * (the latter completes login and emits {@code ConnectEvent}).
     *
     * @param botId the bot user / application id
     * @return the ordered handshake dispatches
     */
    public @NotNull List<Dispatch> handshake(long botId) {
        List<Dispatch> dispatches = new ArrayList<>();
        dispatches.add(this.ready(botId));
        dispatches.add(GatewayStateChange.connected());
        log.debug("Built startup handshake ({} dispatches) for bot {}", dispatches.size(), botId);
        return dispatches;
    }

}
