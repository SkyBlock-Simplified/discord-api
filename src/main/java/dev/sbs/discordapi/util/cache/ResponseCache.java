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
        @Getter private @NotNull Response currentResponse;
        @Getter private final @NotNull ConcurrentMap<String, Entry> followups = Concurrent.newMap();
        @Getter private @NotNull Optional<Modal> activeModal = Optional.empty();
        @Getter private long lastInteract = System.currentTimeMillis();
        @Getter private boolean loading;
        @Getter private boolean busy;
        @Getter private boolean deferred;

        public Entry(@NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
            this.channelId = channelId;
            this.userId = userId;
            this.messageId = messageId;
            this.response = response;
            this.currentResponse = response;
            this.busy = true;
            this.loading = true;
            this.deferred = false;
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
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Entry entry = (Entry) o;

            return new EqualsBuilder()
                .append(this.getLastInteract(), entry.getLastInteract())
                .append(this.isBusy(), entry.isBusy())
                .append(this.isLoading(), entry.isLoading())
                .append(this.isDeferred(), entry.isDeferred())
                .append(this.getChannelId(), entry.getChannelId())
                .append(this.getUserId(), entry.getUserId())
                .append(this.getMessageId(), entry.getMessageId())
                .append(this.getResponse(), entry.getResponse())
                .append(this.getCurrentResponse(), entry.getCurrentResponse())
                .append(this.getFollowups(), entry.getFollowups())
                .append(this.getActiveModal(), entry.getActiveModal())
                .build();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                .append(this.getChannelId())
                .append(this.getUserId())
                .append(this.getMessageId())
                .append(this.getResponse())
                .append(this.getCurrentResponse())
                .append(this.getFollowups())
                .append(this.getActiveModal())
                .append(this.getLastInteract())
                .append(this.isBusy())
                .append(this.isLoading())
                .append(this.isDeferred())
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

        public boolean isModified() {
            return !this.getCurrentResponse().equals(this.getResponse()) || this.getResponse().isCacheUpdateRequired();
        }

        /**
         * Sets this response as busy, preventing it from being removed from the {@link DiscordBot#getResponseCache()}.
         */
        public void setBusy() {
            this.busy = true;
        }

        public void setDeferred() {
            this.deferred = true;
        }

        /**
         * Sets this response as loaded, preventing it from being edited through {@link EventContext#reply}}.
         */
        public void setLoaded() {
            this.loading = false;
        }

        public void setActiveModal(@NotNull Modal modal) {
            this.activeModal = Optional.of(modal);
        }

        /**
         * Updates this response as not busy, allowing it to be later removed from the {@link DiscordBot#getResponseCache()}.
         */
        public Entry updateLastInteract() {
            this.currentResponse = this.response;
            this.lastInteract = System.currentTimeMillis();
            this.response.setNoCacheUpdateRequired();
            this.busy = false;
            this.deferred = false;
            return this;
        }

        public Entry updateResponse(@NotNull Response response) {
            this.response = response;
            return this;
        }

    }

}
