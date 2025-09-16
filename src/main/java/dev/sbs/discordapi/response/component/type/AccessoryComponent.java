package dev.sbs.discordapi.response.component.type;

import dev.sbs.discordapi.response.component.Component;
import discord4j.core.object.component.IAccessoryComponent;
import org.jetbrains.annotations.NotNull;

public interface AccessoryComponent extends Component {

    @Override
    @NotNull IAccessoryComponent getD4jComponent();

}
