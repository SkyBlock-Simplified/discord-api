package dev.sbs.discordapi.util;

import dev.sbs.api.util.concurrent.Concurrent;
import dev.sbs.api.util.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.stream.IntStream;

public final class DiscordShardHandler extends DiscordObject {

    public DiscordShardHandler(DiscordBot discordBot) {
        super(discordBot);
    }

    public Optional<DiscordShard> getShard(int shardId) {
        return this._getShard(this.getDiscordBot().getGateway(), shardId);
    }

    public Integer getShardCount() {
        return this.getDiscordBot().getGateway().getGatewayClientGroup().getShardCount();
    }

    public Optional<DiscordShard> getShardOfGuild(@NotNull Guild guild) {
        return this.getShardOfGuild(guild.getId());
    }

    public Optional<DiscordShard> getShardOfGuild(@NotNull Snowflake guildId) {
        return this._getShard(
            this.getDiscordBot().getGateway(),
            this.getDiscordBot().getGateway().getGatewayClientGroup().computeShardIndex(guildId)
        );
    }

    public ConcurrentList<DiscordShard> getShards() {
        return IntStream.range(0, this.getShardCount() + 1)
            .mapToObj(shardId -> this._getShard(this.getDiscordBot().getGateway(), shardId))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Concurrent.toList());
    }

    private Optional<DiscordShard> _getShard(GatewayDiscordClient gatewayDiscordClient, int shardId) {
        return gatewayDiscordClient.getGatewayClient(shardId).map(gatewayClient -> new DiscordShard(this.getDiscordBot(), gatewayClient));
    }

}
