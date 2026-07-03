package dev.simplified.discordapi.handler.response;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.discordapi.component.interaction.Modal;
import dev.simplified.discordapi.response.Emoji;
import dev.simplified.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.reaction.Reaction;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache entry pairing a {@link Response} with the Discord identifiers of the
 * message backing it. A single {@code CachedResponse} type represents both
 * top-level replies and followup messages: followups are independent entries
 * whose {@link #getParentId() parentId} points at their parent's
 * {@link #getUniqueId() uniqueId}, allowing the locator to apply uniform
 * expiry, lookup, and cascade-delete logic across both kinds.
 *
 * <p>
 * Lifecycle is tracked through a single {@link State} enum that replaces the
 * previous {@code busy}/{@code deferred} two-flag scheme, eliminating
 * impossible combinations and improving readability at call sites.
 *
 * <p>
 * The {@link Response} is the single source of truth for content - dirty
 * checking flows through {@link Response#isCacheUpdateRequired()} rather than
 * an equality-based snapshot.
 *
 * @see ResponseLocator
 * @see NavState
 */
public final class CachedResponse {

    /** Lifecycle states for a cached entry. */
    public enum State {

        /** No interaction is currently in flight; the entry is eligible for expiry checks. */
        IDLE,

        /** A handler is actively processing an interaction targeting this entry, not yet acknowledged. */
        BUSY,

        /** The interaction was acknowledged with a deferral; its real response is still pending. */
        DEFERRED,

        /** The interaction was acknowledged with a response (edit or modal); further output uses the webhook. */
        ACKNOWLEDGED

    }

    private final @NotNull UUID uniqueId;
    private final @NotNull Snowflake messageId;
    private final @NotNull Snowflake channelId;
    private final @NotNull Snowflake userId;
    private final @NotNull Optional<Snowflake> guildId;
    private final @NotNull Optional<UUID> parentId;
    private final @NotNull Optional<String> followupIdentifier;
    private final @NotNull ConcurrentMap<Snowflake, Modal> activeModals = Concurrent.newMap();
    private final @NotNull Instant createdAt;
    private final @NotNull Optional<Instant> expiresAt;

    private volatile @NotNull Response response;
    private volatile @NotNull State state;
    private volatile long lastInteract;
    private volatile @NotNull NavState navState;

    private CachedResponse(@NotNull Builder builder) {
        this.uniqueId = builder.uniqueId;
        this.messageId = builder.messageId;
        this.channelId = builder.channelId;
        this.userId = builder.userId;
        this.guildId = builder.guildId;
        this.parentId = builder.parentId;
        this.followupIdentifier = builder.followupIdentifier;
        this.createdAt = builder.createdAt;
        this.expiresAt = builder.expiresAt;
        this.response = builder.response;
        this.state = builder.state;
        this.lastInteract = builder.lastInteract;
        this.navState = builder.navState;
    }

    /** Returns a new builder for constructing a {@code CachedResponse}. */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /** The stable identifier shared with the wrapped {@link Response#getUniqueId()}. */
    public @NotNull UUID getUniqueId() {
        return this.uniqueId;
    }

    /** The Discord message snowflake of the message backing this entry. */
    public @NotNull Snowflake getMessageId() {
        return this.messageId;
    }

    /** The Discord channel snowflake of the channel containing the message. */
    public @NotNull Snowflake getChannelId() {
        return this.channelId;
    }

    /** The Discord user snowflake of the user who triggered creation of this entry. */
    public @NotNull Snowflake getUserId() {
        return this.userId;
    }

    /** The Discord guild snowflake, or empty for direct-message contexts. */
    public @NotNull Optional<Snowflake> getGuildId() {
        return this.guildId;
    }

    /**
     * The {@link #getUniqueId() uniqueId} of the parent entry when this entry
     * represents a followup, or empty for top-level entries.
     */
    public @NotNull Optional<UUID> getParentId() {
        return this.parentId;
    }

    /** Optional user-supplied identifier for the followup, used to address it by name. */
    public @NotNull Optional<String> getFollowupIdentifier() {
        return this.followupIdentifier;
    }

    /** The current {@link Response} bound to this entry. */
    public @NotNull Response getResponse() {
        return this.response;
    }

    /** The lifecycle state of this entry. */
    public @NotNull State getState() {
        return this.state;
    }

    /** Epoch millisecond timestamp of the most recent user interaction with this entry. */
    public long getLastInteract() {
        return this.lastInteract;
    }

    /** The most recent {@link NavState navigation state} captured for persistence. */
    public @NotNull NavState getNavState() {
        return this.navState;
    }

    /** Timestamp at which this entry was created. */
    public @NotNull Instant getCreatedAt() {
        return this.createdAt;
    }

    /**
     * Optional expiration instant for this entry. Empty indicates the entry
     * never expires.
     */
    public @NotNull Optional<Instant> getExpiresAt() {
        return this.expiresAt;
    }

    /** Per-user modal map for matching modal-submit events back to this entry. */
    public @NotNull ConcurrentMap<Snowflake, Modal> getActiveModals() {
        return this.activeModals;
    }

    /** {@code true} if this entry represents a followup of another entry. */
    public boolean isFollowup() {
        return this.parentId.isPresent();
    }

    /**
     * {@code true} if this entry's bound {@link Response} signals a pending
     * cache update via {@link Response#isCacheUpdateRequired()}, indicating
     * a render is needed before the next user-visible state change.
     */
    public boolean isModified() {
        return this.response.isCacheUpdateRequired();
    }

    /**
     * {@code true} while a handler is processing an interaction OR the
     * entry's time-to-live has not yet elapsed since the last interaction.
     * Inactive entries are eligible for eviction by the scheduled cleanup loop.
     */
    public boolean isActive() {
        if (this.state != State.IDLE)
            return true;

        return System.currentTimeMillis() < this.lastInteract + (this.response.getTimeToLive() * 1000L);
    }

    /** Inverse of {@link #isActive()}. */
    public boolean notActive() {
        return !this.isActive();
    }

    /** Marks this entry as currently being processed. */
    public void setBusy() {
        this.state = State.BUSY;
    }

    /** Marks this entry as deferred (the initial Discord ack has been sent). */
    public void setDeferred() {
        this.state = State.DEFERRED;
    }

    /** Marks this entry's interaction as acknowledged with a response (an edit or a presented modal). */
    public void setAcknowledged() {
        this.state = State.ACKNOWLEDGED;
    }

    /**
     * Whether the current interaction has already been acknowledged to Discord - deferred or responded.
     * Discord permits exactly one interaction callback, so subsequent output must use the webhook.
     *
     * @return {@code true} if the state is {@link State#DEFERRED} or {@link State#ACKNOWLEDGED}
     */
    public boolean isAcknowledged() {
        return this.state == State.DEFERRED || this.state == State.ACKNOWLEDGED;
    }

    /** Replaces the bound {@link Response} with the given updated instance. */
    public Mono<CachedResponse> updateResponse(@NotNull Response response) {
        this.response = response;
        return Mono.just(this);
    }

    /** Replaces the navigation snapshot with the given updated state. */
    public void setNavState(@NotNull NavState navState) {
        this.navState = navState;
    }

    /**
     * Records the current time as the last interaction, clears the dirty flag
     * on the bound response, and transitions back to {@link State#IDLE}.
     */
    public Mono<CachedResponse> updateLastInteract() {
        return Mono.fromRunnable(() -> {
            this.response.setNoCacheUpdateRequired();
            this.lastInteract = System.currentTimeMillis();
            this.state = State.IDLE;
        });
    }

    /**
     * Updates this entry's response attachments from the given Discord
     * message after a send/edit completes.
     */
    public Mono<CachedResponse> updateAttachments(@NotNull Message message) {
        return Mono.fromRunnable(() -> this.response.updateAttachments(message));
    }

    /**
     * Synchronizes the Discord message's reactions with those declared on
     * this entry's current page, removing stale reactions and adding any
     * missing ones.
     */
    public Mono<CachedResponse> updateReactions(@NotNull Message message) {
        return Mono.just(message)
            .checkpoint("CachedResponse#updateReactions Processing")
            .flatMap(msg -> {
                ConcurrentList<Emoji> newReactions = this.response
                    .getHistoryHandler()
                    .getCurrentPage()
                    .getReactions();

                ConcurrentList<Emoji> currentReactions = msg.getReactions()
                    .stream()
                    .filter(Reaction::selfReacted)
                    .map(Reaction::getEmoji)
                    .map(Emoji::of)
                    .collect(Concurrent.toList());

                Mono<Void> mono = Mono.empty();

                if (currentReactions.stream().anyMatch(emoji -> !newReactions.contains(emoji)))
                    mono = msg.removeAllReactions();

                return mono.then(Mono.when(
                    newReactions.stream()
                        .map(emoji -> msg.addReaction(emoji.getD4jReaction()))
                        .collect(Concurrent.toList())
                ));
            })
            .thenReturn(this);
    }

    /** Returns the active modal dialog for the given user, if one exists. */
    public @NotNull Optional<Modal> getUserModal(@NotNull User user) {
        return Optional.ofNullable(this.activeModals.getOrDefault(user.getId(), null));
    }

    /** Associates a modal dialog with the given user for this entry. */
    public void setUserModal(@NotNull User user, @NotNull Modal modal) {
        this.activeModals.put(user.getId(), modal);
    }

    /** Removes the active modal dialog for the given user. */
    public void clearModal(@NotNull User user) {
        this.activeModals.remove(user.getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CachedResponse that = (CachedResponse) o;
        return Objects.equals(this.uniqueId, that.uniqueId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.uniqueId);
    }

    /**
     * Mutable builder for assembling a {@link CachedResponse}. Used by the
     * locator implementation when promoting a sent message into the cache.
     */
    public static final class Builder {

        private UUID uniqueId;
        private Snowflake messageId;
        private Snowflake channelId;
        private Snowflake userId;
        private Optional<Snowflake> guildId = Optional.empty();
        private Optional<UUID> parentId = Optional.empty();
        private Optional<String> followupIdentifier = Optional.empty();
        private Instant createdAt = Instant.now();
        private Optional<Instant> expiresAt = Optional.empty();
        private Response response;
        private State state = State.BUSY;
        private long lastInteract = System.currentTimeMillis();
        private NavState navState = NavState.empty();

        private Builder() {}

        /** Sets the stable response identifier (typically {@link Response#getUniqueId()}). */
        public @NotNull Builder withUniqueId(@NotNull UUID uniqueId) {
            this.uniqueId = uniqueId;
            return this;
        }

        /** Sets the Discord message snowflake. */
        public @NotNull Builder withMessageId(@NotNull Snowflake messageId) {
            this.messageId = messageId;
            return this;
        }

        /** Sets the Discord channel snowflake. */
        public @NotNull Builder withChannelId(@NotNull Snowflake channelId) {
            this.channelId = channelId;
            return this;
        }

        /** Sets the Discord user snowflake of the entry owner. */
        public @NotNull Builder withUserId(@NotNull Snowflake userId) {
            this.userId = userId;
            return this;
        }

        /** Sets the Discord guild snowflake, or empty for direct-message contexts. */
        public @NotNull Builder withGuildId(@NotNull Optional<Snowflake> guildId) {
            this.guildId = guildId;
            return this;
        }

        /** Marks this entry as a followup of the given parent {@code uniqueId}. */
        public @NotNull Builder withParentId(@NotNull UUID parentId) {
            this.parentId = Optional.of(parentId);
            return this;
        }

        /** Sets the optional user-supplied followup identifier. */
        public @NotNull Builder withFollowupIdentifier(@NotNull String identifier) {
            this.followupIdentifier = Optional.of(identifier);
            return this;
        }

        /** Sets the optional user-supplied followup identifier. */
        public @NotNull Builder withFollowupIdentifier(@NotNull Optional<String> identifier) {
            this.followupIdentifier = identifier;
            return this;
        }

        /** Sets the entry creation timestamp. */
        public @NotNull Builder withCreatedAt(@NotNull Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /** Sets the optional expiration timestamp. */
        public @NotNull Builder withExpiresAt(@NotNull Optional<Instant> expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        /** Sets the bound {@link Response}. */
        public @NotNull Builder withResponse(@NotNull Response response) {
            this.response = response;
            return this;
        }

        /** Sets the initial lifecycle state. Defaults to {@link State#BUSY}. */
        public @NotNull Builder withState(@NotNull State state) {
            this.state = state;
            return this;
        }

        /** Sets the most recent interaction timestamp. */
        public @NotNull Builder withLastInteract(long lastInteract) {
            this.lastInteract = lastInteract;
            return this;
        }

        /** Sets the persisted navigation snapshot. */
        public @NotNull Builder withNavState(@NotNull NavState navState) {
            this.navState = navState;
            return this;
        }

        /** Constructs the immutable-shaped {@link CachedResponse}. */
        public @NotNull CachedResponse build() {
            Objects.requireNonNull(this.uniqueId, "uniqueId");
            Objects.requireNonNull(this.messageId, "messageId");
            Objects.requireNonNull(this.channelId, "channelId");
            Objects.requireNonNull(this.userId, "userId");
            Objects.requireNonNull(this.response, "response");
            return new CachedResponse(this);
        }

    }

}
