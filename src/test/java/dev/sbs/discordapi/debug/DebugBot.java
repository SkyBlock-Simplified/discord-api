package dev.sbs.discordapi.debug;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentSet;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.reference.CommandReference;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.gateway.ShardInfo;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.util.AllowedMentions;
import org.jetbrains.annotations.NotNull;

public final class DebugBot extends DiscordBot {

    private DebugBot() {
        super(new DebugConfig(
            SimplifiedApi.getCurrentDirectory(),
            "debug"
        ));
    }

    public static void main(final String[] args) {
        new DebugBot();
    }

    @Override
    protected @NotNull ConcurrentSet<Class<? extends CommandReference>> getCommands() {
        return Concurrent.newUnmodifiableSet(
            Reflection.getResources()
                .filterPackage("dev.sbs.discordapi.debug.command")
                .getSubtypesOf(CommandReference.class)
        );
    }

    @Override
    protected @NotNull AllowedMentions getDefaultAllowedMentions() {
        return AllowedMentions.suppressEveryone();
    }

    @Override
    public @NotNull IntentSet getDisabledIntents() {
        return IntentSet.of(Intent.GUILD_PRESENCES);
    }

    @Override
    protected @NotNull ClientPresence getInitialPresence(ShardInfo shardInfo) {
        return ClientPresence.online(ClientActivity.watching("debugging"));
    }

}
