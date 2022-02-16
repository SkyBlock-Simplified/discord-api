package dev.sbs.discordapi.util;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.discordapi.DiscordBot;
import discord4j.common.close.CloseStatus;
import discord4j.core.DiscordClient;
import discord4j.core.shard.GatewayBootstrap;
import discord4j.gateway.GatewayClient;
import discord4j.rest.util.Multimap;
import discord4j.rest.util.RouteUtils;
import lombok.Getter;
import reactor.core.publisher.Mono;

import java.time.Duration;

public class DiscordShard extends DiscordObject {

    @Getter private final GatewayClient gatewayClient;

    public DiscordShard(DiscordBot discordBot, GatewayClient gatewayClient) {
        super(discordBot);
        this.gatewayClient = gatewayClient;
    }

    private DiscordClient getClient() {
        return this.getDiscordBot().getClient();
    }

    public boolean isConnected() {
        return this.getGatewayClient().isConnected().blockOptional().orElse(false);
    }

    public Duration getResponseTime() {
        return this.getGatewayClient().getResponseTime();
    }

    public Mono<Void> restart() {
        return this.stop().then(this.start());
    }

    @SuppressWarnings("unchecked")
    public Mono<Void> start() {
        return !this.isConnected() ? this.getClient()
            .getGatewayService()
            .getGateway()
            .flatMap(gatewayData -> this.getGatewayClient()
                .execute(
                    RouteUtils.expandQuery(
                        gatewayData.url(),
                        Reflection.of(GatewayBootstrap.class).invokeMethod(Multimap.class, this.getClient().gateway())
                    )
                )
            ) : Mono.empty();
    }

    public Mono<CloseStatus> stop() {
        return this.stop(true);
    }

    public Mono<CloseStatus> stop(boolean allowResume) {
        return this.isConnected() ? this.getGatewayClient().close(allowResume) : Mono.empty();
    }

}
