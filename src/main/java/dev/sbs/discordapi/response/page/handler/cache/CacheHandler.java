package dev.sbs.discordapi.response.page.handler.cache;

public interface CacheHandler {

    boolean isCacheUpdateRequired();

    default void setCacheUpdateRequired() {
        this.setCacheUpdateRequired(true);
    }

    void setCacheUpdateRequired(boolean value);

}
