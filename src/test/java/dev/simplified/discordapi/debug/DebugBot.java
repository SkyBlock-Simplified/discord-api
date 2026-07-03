package dev.simplified.discordapi.debug;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.handler.DiscordConfig;
import dev.simplified.reflection.Reflection;
import dev.simplified.util.Logging;
import dev.simplified.util.NumberUtil;
import dev.simplified.util.SystemUtil;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.shard.MemberRequestFilter;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.util.AllowedMentions;
import org.jetbrains.annotations.NotNull;

public final class DebugBot extends DiscordBot {

    private DebugBot(@NotNull DiscordConfig discordConfig) {
        super(discordConfig);
    }

    public static void main(final String[] args) {
        DiscordConfig discordConfig = DiscordConfig.builder()
            .withToken(SystemUtil.getEnv("SBS_DEBUG_TOKEN"))
            .withMainGuildId(652148034448261150L)
            .withLogChannelId(SystemUtil.getEnv("DEVELOPER_ERROR_LOG_CHANNEL_ID").map(NumberUtil::tryParseLong))
            .withCommands(
                Reflection.getResources()
                    .filterPackage("dev.simplified.discordapi.debug.command")
                    .getTypesOf(DiscordCommand.class)
            )
            .withEmojis(Reflection.getResources(DebugBot.class.getClassLoader()).getResources("emojis/"))
            .withAllowedMentions(AllowedMentions.suppressEveryone())
            .withDisabledIntents(IntentSet.of(Intent.GUILD_PRESENCES))
            .withClientPresence(ClientPresence.doNotDisturb(ClientActivity.watching("debugging")))
            .withMemberRequestFilter(MemberRequestFilter.all())
            .withLogLevel(Logging.Level.INFO)
            .build();

        DebugBot debugBot = new DebugBot(discordConfig);
        debugBot.start();
    }

}
