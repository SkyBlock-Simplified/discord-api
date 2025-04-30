package dev.sbs.discordapi.response.component.interaction.action;

import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.type.UserInteractComponent;
import org.jetbrains.annotations.NotNull;

public interface ActionComponent extends Component, UserInteractComponent {

    @NotNull discord4j.core.object.component.ActionComponent getD4jComponent();

}
