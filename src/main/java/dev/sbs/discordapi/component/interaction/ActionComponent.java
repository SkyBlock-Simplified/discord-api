package dev.sbs.discordapi.component.interaction;

import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.type.UserInteractComponent;
import org.jetbrains.annotations.NotNull;

public interface ActionComponent extends Component, UserInteractComponent {

    @NotNull discord4j.core.object.component.ActionComponent getD4jComponent();

}
