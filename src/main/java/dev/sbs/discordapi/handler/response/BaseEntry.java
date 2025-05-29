package dev.sbs.discordapi.handler.response;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Message;
import discord4j.core.object.reaction.Reaction;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class BaseEntry {

    private final @NotNull Snowflake channelId;
    private final @NotNull Snowflake userId;
    private final @NotNull Snowflake messageId;
    private @NotNull Response response;
    @Getter(AccessLevel.PROTECTED)
    private @NotNull Response currentResponse;

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

    public Mono<CachedResponse> updateAttachments(@NotNull Message message) {
        return Mono.fromRunnable(() -> this.getResponse().updateAttachments(message));
    }

    public Mono<BaseEntry> updateReactions(@NotNull Message message) {
        return Mono.just(message)
            .checkpoint("ResponseHandler#updateReactions Processing")
            .flatMap(msg -> {
                // Update Reactions
                ConcurrentList<Emoji> newReactions = this.getResponse()
                    .getHistoryHandler()
                    .getCurrentPage()
                    .getReactions();

                // Current Reactions
                ConcurrentList<Emoji> currentReactions = msg.getReactions()
                    .stream()
                    .filter(Reaction::selfReacted)
                    .map(Reaction::getEmoji)
                    .map(Emoji::of)
                    .collect(Concurrent.toList());

                Mono<Void> mono = Mono.empty();

                // Remove Existing Reactions
                if (currentReactions.stream().anyMatch(messageEmoji -> !newReactions.contains(messageEmoji)))
                    mono = msg.removeAllReactions();

                return mono.then(Mono.when(
                    newReactions.stream()
                        .map(emoji -> msg.addReaction(emoji.getD4jReaction()))
                        .collect(Concurrent.toList())
                ));
            })
            .thenReturn(this);
    }

}