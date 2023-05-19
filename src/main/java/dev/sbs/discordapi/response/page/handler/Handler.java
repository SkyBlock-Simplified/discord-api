package dev.sbs.discordapi.response.page.handler;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Handler {

    @Getter @Setter private boolean cacheUpdateRequired = false;

    public final void setCacheUpdateRequired() {
        this.setCacheUpdateRequired(true);
    }

}
