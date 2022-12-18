package dev.sbs.discordapi.response.component.interaction.action;

import dev.sbs.discordapi.response.component.interaction.InteractionComponent;
import dev.sbs.discordapi.response.component.type.D4jComponent;

public abstract class ActionComponent extends InteractionComponent implements D4jComponent {

    public abstract discord4j.core.object.component.ActionComponent getD4jComponent();

}
