package dev.sbs.discordapi.handler.response;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.collection.concurrent.ConcurrentMap;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.Modal;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.User;
import discord4j.discordjson.Id;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Getter
public final class CachedResponse extends BaseEntry {

    private final @NotNull ConcurrentList<Followup> followups = Concurrent.newList();
    private final @NotNull ConcurrentMap<Snowflake, Modal> activeModals = Concurrent.newMap();
    private long lastInteract = System.currentTimeMillis();
    private boolean busy;
    private boolean deferred;

    public CachedResponse(@NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
        super(channelId, userId, messageId, response, response);
        this.busy = true;
        this.deferred = false;
    }

    public Mono<Followup> addFollowup(@NotNull String identifier, @NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
        Followup followup = new Followup(identifier, channelId, userId, messageId, response);
        this.followups.add(followup);
        return Mono.just(followup);
    }

    public boolean containsFollowup(@NotNull String identifier) {
        return this.getFollowups()
            .stream()
            .anyMatch(followup -> followup.getIdentifier().equals(identifier));
    }

    public boolean containsFollowup(@NotNull Snowflake messageId) {
        return this.getFollowups()
            .stream()
            .anyMatch(followup -> followup.getMessageId().equals(messageId));
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

    public void clearModal(@NotNull User user) {
        this.activeModals.remove(user.getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        CachedResponse entry = (CachedResponse) o;

        return new EqualsBuilder()
            .append(this.getLastInteract(), entry.getLastInteract())
            .append(this.isBusy(), entry.isBusy())
            .append(this.isDeferred(), entry.isDeferred())
            .append(this.getFollowups(), entry.getFollowups())
            .append(this.getActiveModals(), entry.getActiveModals())
            .build();
    }

    public @NotNull Optional<Modal> getUserModal(@NotNull User user) {
        return Optional.ofNullable(this.activeModals.getOrDefault(user.getId(), null));
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(this.getFollowups())
            .append(this.getActiveModals())
            .append(this.getLastInteract())
            .append(this.isBusy())
            .append(this.isDeferred())
            .build();
    }

    public boolean matchesMessage(@NotNull Snowflake messageId, @NotNull Snowflake userId) {
        return this.getUserId().equals(userId) && (this.getMessageId().equals(messageId) || this.containsFollowup(messageId));
    }

    public boolean matchesMessage(@NotNull Snowflake messageId, @NotNull Id userId) {
        return this.getUserId().asLong() == userId.asLong() && (this.getMessageId().equals(messageId) || this.containsFollowup(messageId));
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

    @Override
    public boolean isFollowup() {
        return true;
    }

    public boolean notActive() {
        return !this.isActive();
    }

    /**
     * Sets this response as busy, preventing it from being removed from the {@link DiscordBot#getResponseHandler()}.
     */
    public void setBusy() {
        this.busy = true;
    }

    public void setDeferred() {
        this.deferred = true;
    }

    public void setUserModal(@NotNull User user, @NotNull Modal modal) {
        this.activeModals.put(user.getId(), modal);
    }

    /**
     * Updates this response as not busy, allowing it to be later removed from the {@link DiscordBot#getResponseHandler()}.
     */
    public Mono<CachedResponse> updateLastInteract() {
        return Mono.fromRunnable(() -> {
            super.processLastInteract();
            this.lastInteract = System.currentTimeMillis();
            this.busy = false;
            this.deferred = false;
        });
    }

    public Mono<CachedResponse> updateResponse(@NotNull Response response) {
        super.setUpdatedResponse(response);
        return Mono.just(this);
    }

}