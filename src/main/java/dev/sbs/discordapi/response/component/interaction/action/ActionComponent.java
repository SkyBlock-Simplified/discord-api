package dev.sbs.discordapi.response.component.interaction.action;

import dev.sbs.discordapi.response.component.interaction.InteractionComponent;
import dev.sbs.discordapi.response.component.type.D4jComponent;
import dev.sbs.discordapi.response.component.type.PreservableComponent;

public abstract class ActionComponent extends InteractionComponent implements PreservableComponent, D4jComponent {

    public abstract discord4j.core.object.component.ActionComponent getD4jComponent();

}
