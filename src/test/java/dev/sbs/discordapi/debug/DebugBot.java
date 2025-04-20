package dev.sbs.discordapi.debug;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.NumberUtil;
import dev.sbs.api.util.StringUtil;
import dev.sbs.api.util.SystemUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.DiscordConfig;
import dev.sbs.discordapi.command.DiscordCommand;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.shard.MemberRequestFilter;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.util.AllowedMentions;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;

public final class DebugBot extends DiscordBot {

    public static void main(final String[] args) {
        DiscordConfig discordConfig = DiscordConfig.builder()
            .withToken(SystemUtil.getEnv("DISCORD_TOKEN"))
            .withMainGuildId(652148034448261150L)
            .withDebugChannelId(SystemUtil.getEnv("DEVELOPER_ERROR_LOG_CHANNEL_ID").map(NumberUtil::tryParseLong))
            .withCommands(
                Reflection.getResources()
                    .filterPackage("dev.sbs.discordapi.debug.command")
                    .getTypesOf(DiscordCommand.class)
            )
            .withAllowedMentions(AllowedMentions.suppressEveryone())
            .withDisabledIntents(IntentSet.of(Intent.GUILD_PRESENCES))
            .withClientPresence(ClientPresence.doNotDisturb(ClientActivity.watching("debugging")))
            .withMemberRequestFilter(MemberRequestFilter.all())
            .withLogLevel(Level.INFO)
            .build();

        DebugBot debugBot = new DebugBot();
        debugBot.login(discordConfig);
    }

    @Override
    protected void onGatewayConnected(@NotNull GatewayDiscordClient gatewayDiscordClient) {
        SystemUtil.getEnv("HYPIXEL_API_KEY")
            .map(StringUtil::toUUID)
            .ifPresent(value -> SimplifiedApi.getKeyManager().add("HYPIXEL_API_KEY", value));
    }

}
