package dev.sbs.discordapi.handler.shard;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.util.DiscordReference;
import discord4j.common.close.CloseStatus;
import discord4j.core.DiscordClient;
import discord4j.core.shard.GatewayBootstrap;
import discord4j.gateway.GatewayClient;
import discord4j.rest.util.Multimap;
import discord4j.rest.util.RouteUtils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Getter
public class Shard extends DiscordReference {

    private final @NotNull GatewayClient gatewayClient;

    public Shard(@NotNull DiscordBot discordBot, @NotNull GatewayClient gatewayClient) {
        super(discordBot);
        this.gatewayClient = gatewayClient;
    }

    private @NotNull DiscordClient getClient() {
        return this.getDiscordBot().getClient();
    }

    public boolean isConnected() {
        return this.getGatewayClient().isConnected().blockOptional().orElse(false);
    }

    public @NotNull Duration getResponseTime() {
        return this.getGatewayClient().getResponseTime();
    }

    public @NotNull Mono<Void> restart() {
        return this.stop().then(this.start());
    }

    @SuppressWarnings("unchecked")
    public @NotNull Mono<Void> start() {
        return !this.isConnected() ? this.getClient()
            .getGatewayService()
            .getGateway()
            .flatMap(gatewayData -> this.getGatewayClient().execute(
                RouteUtils.expandQuery(
                    gatewayData.url(),
                    Reflection.of(GatewayBootstrap.class).invokeMethod(Multimap.class, this.getClient().gateway())
                )
            )) : Mono.empty();
    }

    public @NotNull Mono<CloseStatus> stop() {
        return this.stop(true);
    }

    public @NotNull Mono<CloseStatus> stop(boolean allowResume) {
        return this.isConnected() ? this.getGatewayClient().close(allowResume) : Mono.empty();
    }

}
