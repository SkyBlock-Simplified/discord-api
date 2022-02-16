package dev.sbs.discordapi.response.component;

import discord4j.core.object.component.MessageComponent;

public abstract class Component {

    public abstract MessageComponent getD4jComponent();

    public abstract boolean isPreserved();

    public final boolean notPreserved() {
        return !this.isPreserved();
    }

}
