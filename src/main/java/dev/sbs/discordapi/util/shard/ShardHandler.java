package dev.sbs.discordapi.util.shard;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.util.base.DiscordHelper;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.stream.IntStream;

public final class ShardHandler extends DiscordHelper {

    public ShardHandler(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    public @NotNull Optional<Shard> getShard(int shardId) {
        return this._getShard(this.getDiscordBot().getGateway(), shardId);
    }

    public int getShardCount() {
        return this.getDiscordBot().getGateway().getGatewayClientGroup().getShardCount();
    }

    public @NotNull Optional<Shard> getShardOfGuild(@NotNull Guild guild) {
        return this.getShardOfGuild(guild.getId());
    }

    public @NotNull Optional<Shard> getShardOfGuild(@NotNull Snowflake guildId) {
        return this._getShard(
            this.getDiscordBot().getGateway(),
            this.getDiscordBot().getGateway().getGatewayClientGroup().computeShardIndex(guildId)
        );
    }

    public @NotNull ConcurrentList<Shard> getShards() {
        return IntStream.range(0, this.getShardCount() + 1)
            .mapToObj(shardId -> this._getShard(this.getDiscordBot().getGateway(), shardId))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Concurrent.toList());
    }

    private @NotNull Optional<Shard> _getShard(GatewayDiscordClient gatewayDiscordClient, int shardId) {
        return gatewayDiscordClient.getGatewayClient(shardId).map(gatewayClient -> new Shard(this.getDiscordBot(), gatewayClient));
    }

}
