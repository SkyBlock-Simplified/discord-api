package dev.sbs.discordapi.listener.lifecycle;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;

/**
 * Listener for guild create events, triggering a refresh of guild-scoped
 * application commands when the bot joins or reconnects to a guild.
 */
public class GuildCreateListener extends DiscordListener<GuildCreateEvent> {

    /**
     * Constructs a new {@code GuildCreateListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public GuildCreateListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public Publisher<Void> apply(@NotNull GuildCreateEvent guildCreateEvent) {
        return this.getDiscordBot()
            .getCommandHandler()
            .updateGuildApplicationCommands(guildCreateEvent.getGuild().getId().asLong());
    }

}
