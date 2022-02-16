package dev.sbs.discordapi.util;

import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.MessageChannel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

public final class DiscordResponseCache extends ConcurrentList<DiscordResponseCache.Entry> {

    /**
     * Adds a {@link Response} and it's assigned {@link MessageChannel}, User ID and Message ID.
     *
     * @param channelId the discord channel id of the response
     * @param messageId the discord response id of the response
     * @param response the response to cache
     */
    public Entry add(@NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
        Entry entry = new Entry(channelId, userId, messageId, response);
        this.add(entry);
        return entry;
    }

    public Optional<Entry> getEntry(UUID uniqueId) {
        return this.stream()
            .filter(entry -> entry.getResponse().getUniqueId().equals(uniqueId))
            .findFirst();
    }

    public Optional<Response> getResponse(UUID uniqueId) {
        return this.getEntry(uniqueId).map(Entry::getResponse);
    }

    /**
     * Updates the stored {@link Response}.
     *
     * @param response The response to update.
     */
    public void updateResponse(@NotNull Response response) {
        this.updateResponse(response, true);
    }

    /**
     * Updates the stored {@link Response}.
     *
     * @param response The response to update.
     * @param updateTTL Update the Time To Live and set as non-busy.
     */
    public void updateResponse(@NotNull Response response, boolean updateTTL) {
        this.stream()
            .filter(cachedMessage -> cachedMessage.getResponse().getUniqueId().equals(response.getUniqueId()))
            .findFirst()
            .ifPresent(cachedMessaged -> cachedMessaged.updateResponse(response, updateTTL));
    }

    public static final class Entry {

        @Getter private final Snowflake channelId;
        @Getter private final Snowflake userId;
        @Getter private final Snowflake messageId;
        @Getter private Response response;
        @Getter private long lastInteract = System.currentTimeMillis();
        @Getter private boolean modified;
        @Getter private boolean busy;

        public Entry(Snowflake channelId, Snowflake userId, Snowflake messageId, Response response) {
            this.channelId = channelId;
            this.userId = userId;
            this.messageId = messageId;
            this.response = response;
            this.busy = true;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Entry that = (Entry) obj;

            return new EqualsBuilder()
                .append(this.getChannelId(), that.getChannelId())
                .append(this.getUserId(), that.getUserId())
                .append(this.getMessageId(), that.getMessageId())
                .append(this.getResponse(), that.getResponse())
                .build();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                .append(this.getChannelId())
                .append(this.getUserId())
                .append(this.getMessageId())
                .append(this.getResponse())
                .build();
        }

        /**
         * Checks if this response is busy or not expired.
         * <br><br>
         * See {@link #getLastInteract()} and {@link Response#getTimeToLive()}.
         *
         * @return True if busy or not expired.
         */
        public boolean isActive() {
            return this.isBusy() || System.currentTimeMillis() < this.getLastInteract() + (this.getResponse().getTimeToLive() * 1000L);
        }

        /**
         * Sets this response as busy, preventing it from being removed from the {@link DiscordBot#getResponseCache()}.
         */
        public void setBusy() {
            this.busy = true;
        }

        public void setUpdated() {
            this.modified = false;
        }

        /**
         * Updates this response as not busy, allowing it to be later removed from the {@link DiscordBot#getResponseCache()}.
         */
        public void updateLastInteract() {
            this.lastInteract = System.currentTimeMillis();
            this.busy = false;
        }

        public void updateResponse(Response response) {
            this.updateResponse(response, true);
        }

        public void updateResponse(Response response, boolean updateTTL) {
            this.response = response;
            this.modified = true;

            if (updateTTL)
                this.updateLastInteract();
        }

    }

}
