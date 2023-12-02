package dev.sbs.discordapi.response.component.interaction.action;

import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.type.D4jComponent;
import dev.sbs.discordapi.response.component.type.IdentifiableComponent;
import org.jetbrains.annotations.NotNull;

public abstract class ActionComponent extends Component implements IdentifiableComponent, D4jComponent {

    public abstract @NotNull discord4j.core.object.component.ActionComponent getD4jComponent();

}
