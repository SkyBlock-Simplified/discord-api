package dev.sbs.discordapi.util.cache;

import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.collection.concurrent.ConcurrentMap;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.Modal;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.MessageChannel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class ResponseCache extends ConcurrentList<ResponseCache.Entry> {

    /**
     * Adds a {@link Response} and it's assigned {@link MessageChannel}, User ID and Message ID.
     *
     * @param channelId the discord channel id of the response
     * @param userId the user interacting with the response
     * @param messageId the discord response id of the response
     * @param response the response to cache
     */
    public Entry createAndGet(@NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
        Entry entry = new Entry(channelId, userId, messageId, response);
        this.add(entry);
        return entry;
    }

    public static final class Entry {

        @Getter private final @NotNull Snowflake channelId;
        @Getter private final @NotNull Snowflake userId;
        @Getter private final @NotNull Snowflake messageId;
        @Getter private @NotNull Response response;
        @Getter private final @NotNull ConcurrentMap<String, Entry> followups = Concurrent.newMap();
        @Getter private @NotNull Optional<Modal> activeModal = Optional.empty();
        @Getter private long lastInteract = System.currentTimeMillis();
        @Getter private boolean modified;
        @Getter private boolean busy;
        @Getter private boolean loading;

        public Entry(@NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
            this.channelId = channelId;
            this.userId = userId;
            this.messageId = messageId;
            this.response = response;
            this.busy = true;
            this.loading = true;
        }

        public Entry addFollowup(@NotNull String key, @NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
            Entry entry = new Entry(channelId, userId, messageId, response);
            this.followups.put(key, entry);
            return entry;
        }

        public void removeFollowup(@NotNull String key) {
            this.followups.remove(key);
        }

        public void clearModal() {
            this.activeModal = Optional.empty();
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
            return this.isBusy() || this.isLoading() || System.currentTimeMillis() < this.getLastInteract() + (this.getResponse().getTimeToLive() * 1000L);
        }

        /**
         * Sets this response as busy, preventing it from being removed from the {@link DiscordBot#getResponseCache()}.
         */
        public void setBusy() {
            this.busy = true;
        }

        /**
         * Sets this response as loaded, preventing it from being edited through {@link EventContext#reply}}.
         */
        public void setLoaded() {
            this.loading = false;
        }

        public void setUpdated() {
            this.modified = false;
            this.response.setNoCacheUpdateRequired();
        }

        public void setActiveModal(Modal modal) {
            this.activeModal = Optional.of(modal);
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
