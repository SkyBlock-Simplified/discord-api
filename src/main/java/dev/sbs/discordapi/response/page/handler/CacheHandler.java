package dev.sbs.discordapi.response.page.handler;

public interface CacheHandler {

    boolean isCacheUpdateRequired();

    default void setCacheUpdateRequired() {
        this.setCacheUpdateRequired(true);
    }

    void setCacheUpdateRequired(boolean value);

}
