package dev.simplified.discordapi.event.lifecycle;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.event.BotEvent;
import discord4j.core.GatewayDiscordClient;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Emitted by {@link DiscordBot} when the Discord gateway connection has been
 * successfully established and the {@link GatewayDiscordClient} is available.
 */
@Getter
@RequiredArgsConstructor
public final class GatewayConnectBotEvent implements BotEvent {

    /** The bot instance that emitted this event. */
    private final @NotNull DiscordBot discordBot;

    /** The connected gateway client. */
    private final @NotNull GatewayDiscordClient gatewayDiscordClient;

}
