package dev.sbs.discordapi.handler.response;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.MessageChannel;
import org.jetbrains.annotations.NotNull;

public class ResponseHandler extends ConcurrentList<CachedResponse> {

    /**
     * Adds a {@link Response} and it's assigned {@link MessageChannel}, User ID and Message ID.
     *
     * @param channelId the discord channel id of the response
     * @param userId the user interacting with the response
     * @param messageId the discord response id of the response
     * @param response the response to cache
     */
    public CachedResponse createAndGet(@NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
        CachedResponse entry = new CachedResponse(channelId, userId, messageId, response);
        this.add(entry);
        return entry;
    }

}
