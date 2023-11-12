package dev.sbs.discordapi.util.cache;

import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.Modal;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.MessageChannel;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

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

    public static final class Entry extends BaseEntry {

        @Getter private final @NotNull ConcurrentList<Followup> followups = Concurrent.newList();
        @Getter private @NotNull Optional<Modal> activeModal = Optional.empty();
        @Getter private long lastInteract = System.currentTimeMillis();
        @Getter private boolean loading;
        @Getter private boolean busy;
        @Getter private boolean deferred;

        public Entry(@NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
            super(channelId, userId, messageId, response, response);
            this.busy = true;
            this.loading = true;
            this.deferred = false;
        }

        public Followup addFollowup(@NotNull String identifier, @NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
            Followup followup = new Followup(identifier, channelId, userId, messageId, response);
            this.followups.add(followup);
            return followup;
        }

        public Optional<Followup> findFollowup(@NotNull String identifier) {
            return this.getFollowups().findFirst(Followup::getIdentifier, identifier);
        }

        public Optional<Followup> findFollowup(@NotNull Snowflake messageId) {
            return this.getFollowups().findFirst(Followup::getMessageId, messageId);
        }

        public void removeFollowup(@NotNull String identifier) {
            this.followups.removeIf(followup -> followup.getIdentifier().equals(identifier));
        }

        public void clearModal() {
            this.activeModal = Optional.empty();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            Entry entry = (Entry) o;

            return new EqualsBuilder()
                .append(this.getLastInteract(), entry.getLastInteract())
                .append(this.isLoading(), entry.isLoading())
                .append(this.isBusy(), entry.isBusy())
                .append(this.isDeferred(), entry.isDeferred())
                .append(this.getFollowups(), entry.getFollowups())
                .append(this.getActiveModal(), entry.getActiveModal())
                .build();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                .appendSuper(super.hashCode())
                .append(this.getFollowups())
                .append(this.getActiveModal())
                .append(this.getLastInteract())
                .append(this.isLoading())
                .append(this.isBusy())
                .append(this.isDeferred())
                .build();
        }

        public boolean matchesMessage(@NotNull Snowflake messageId, @NotNull Snowflake userId) {
            return this.getUserId().equals(userId) && (this.getMessageId().equals(messageId) || this.findFollowup(messageId).isPresent());
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

        @Override
        public boolean isFollowup() {
            return true;
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
        public Mono<Entry> updateLastInteract() {
            return Mono.fromRunnable(() -> {
                super.processLastInteract();
                this.lastInteract = System.currentTimeMillis();
                this.busy = false;
                this.deferred = false;
            });
        }

        public Entry updateResponse(@NotNull Response response) {
            super.setUpdatedResponse(response);
            return this;
        }

    }

    public static class Followup extends BaseEntry {

        @Getter private final @NotNull String identifier;

        private Followup(@NotNull String identifier, @NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
            super(channelId, userId, messageId, response, response);
            this.identifier = identifier;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            Followup followup = (Followup) o;

            return new EqualsBuilder()
                .append(this.getIdentifier(), followup.getIdentifier())
                .build();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                .appendSuper(super.hashCode())
                .append(this.getIdentifier())
                .build();
        }

        @Override
        public boolean isFollowup() {
            return true;
        }

        public Followup updateResponse(@NotNull Response response) {
            super.setUpdatedResponse(response);
            return this;
        }

    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static abstract class BaseEntry {

        @Getter private final @NotNull Snowflake channelId;
        @Getter private final @NotNull Snowflake userId;
        @Getter private final @NotNull Snowflake messageId;
        @Getter private @NotNull Response response;
        @Getter private @NotNull Response currentResponse;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BaseEntry baseEntry = (BaseEntry) o;

            return new EqualsBuilder()
                .append(this.getChannelId(), baseEntry.getChannelId())
                .append(this.getUserId(), baseEntry.getUserId())
                .append(this.getMessageId(), baseEntry.getMessageId())
                .append(this.getResponse(), baseEntry.getResponse())
                .append(this.getCurrentResponse(), baseEntry.getCurrentResponse())
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
                .build();
        }

        public abstract boolean isFollowup();

        public boolean isModified() {
            return !this.getCurrentResponse().equals(this.getResponse()) || this.getResponse().isCacheUpdateRequired();
        }

        protected void processLastInteract() {
            this.currentResponse = this.response;
            this.response.setNoCacheUpdateRequired();
        }

        protected void setUpdatedResponse(@NotNull Response response) {
            this.response = response;
        }

    }

}
