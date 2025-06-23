package dev.sbs.discordapi.listener.gateway;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.lifecycle.DisconnectEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

public class DisconnectListener extends DiscordListener<DisconnectEvent> {

    public DisconnectListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public Publisher<Void> apply(@NotNull DisconnectEvent event) {
        return Mono.fromRunnable(() -> {
            this.getDiscordBot().getScheduler().shutdownNow();
            SimplifiedApi.getSessionManager().disconnectAll();
        });
    }

}
