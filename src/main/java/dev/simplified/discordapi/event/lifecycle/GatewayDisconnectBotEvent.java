package dev.simplified.discordapi.event.lifecycle;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.event.BotEvent;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Emitted by {@link DiscordBot} when the Discord gateway has disconnected,
 * mirroring Discord4J's gateway {@code DisconnectEvent} as a bot-internal
 * lifecycle notification.
 */
@Getter
@RequiredArgsConstructor
public final class GatewayDisconnectBotEvent implements BotEvent {

    /** The bot instance that emitted this event. */
    private final @NotNull DiscordBot discordBot;

}
