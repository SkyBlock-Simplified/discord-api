package dev.sbs.discordapi.listener.guild;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;

public class GuildCreateListener extends DiscordListener<GuildCreateEvent> {

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
