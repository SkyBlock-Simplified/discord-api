package dev.sbs.discordapi.response.component.interaction.action;

import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.type.D4jComponent;
import dev.sbs.discordapi.response.component.type.IdentifiableComponent;
import org.jetbrains.annotations.NotNull;

public interface ActionComponent extends Component, IdentifiableComponent, D4jComponent {

    @NotNull discord4j.core.object.component.ActionComponent getD4jComponent();

}
