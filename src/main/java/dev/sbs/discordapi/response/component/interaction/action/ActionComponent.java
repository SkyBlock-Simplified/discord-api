package dev.sbs.discordapi.response.component.interaction.action;

import dev.sbs.discordapi.context.interaction.deferrable.component.ComponentContext;
import dev.sbs.discordapi.response.component.PreservableComponent;
import dev.sbs.discordapi.response.component.interaction.InteractionComponent;

public abstract class ActionComponent<T extends ComponentContext> extends InteractionComponent<T> implements PreservableComponent {

    public abstract discord4j.core.object.component.ActionComponent getD4jComponent();

}
