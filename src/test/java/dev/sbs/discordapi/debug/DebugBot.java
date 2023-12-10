package dev.sbs.discordapi.debug;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.helper.NumberUtil;
import dev.sbs.api.util.helper.ResourceUtil;
import dev.sbs.api.util.helper.StringUtil;
import dev.sbs.discordapi.command.reference.CommandReference;
import dev.sbs.discordapi.util.DiscordConfig;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.shard.MemberRequestFilter;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.util.AllowedMentions;
import org.apache.logging.log4j.Level;

public final class DebugBot {

    public static void main(final String[] args) {
        DiscordConfig.builder()
            .withFileName("config-debug")
            .withToken(ResourceUtil.getEnv("DISCORD_TOKEN"))
            .withMainGuildId(652148034448261150L)
            .withDebugChannelId(ResourceUtil.getEnv("DEVELOPER_ERROR_LOG_CHANNEL_ID").map(NumberUtil::tryParseLong))
            .withCommands(
                Reflection.getResources()
                    .filterPackage("dev.sbs.discordapi.debug.command")
                    .getSubtypesOf(CommandReference.class)
            )
            .withAllowedMentions(AllowedMentions.suppressEveryone())
            .withDisabledIntents(IntentSet.of(Intent.GUILD_PRESENCES))
            .withClientPresence(ClientPresence.doNotDisturb(ClientActivity.watching("debugging")))
            .withMemberRequestFilter(MemberRequestFilter.all())
            .withLogLevel(Level.INFO)
            .onGatewayConnected(gatewayDiscordClient -> ResourceUtil.getEnv("HYPIXEL_API_KEY")
                .map(StringUtil::toUUID)
                .ifPresent(value -> SimplifiedApi.getKeyManager().add("HYPIXEL_API_KEY", value))
            )
            .build()
            .createBot();
    }

}
