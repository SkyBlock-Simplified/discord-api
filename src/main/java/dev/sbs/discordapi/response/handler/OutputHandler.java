package dev.sbs.discordapi.response.handler;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import org.jetbrains.annotations.NotNull;

public interface OutputHandler<T> {

    @NotNull ConcurrentList<T> getItems();

    boolean isCacheUpdateRequired();

    default boolean isEmpty() {
        return this.getItems().isEmpty();
    }

    default boolean notEmpty() {
        return this.getItems().notEmpty();
    }

    default void setCacheUpdateRequired() {
        this.setCacheUpdateRequired(true);
    }

    void setCacheUpdateRequired(boolean cacheUpdateRequired);

}
