package dev.sbs.discordapi.response.component.interaction.action;

import dev.sbs.discordapi.context.interaction.deferrable.component.ComponentContext;

public abstract class UserActionComponent<T extends ComponentContext> extends ActionComponent<T> {

    public abstract boolean isPaging();

}
