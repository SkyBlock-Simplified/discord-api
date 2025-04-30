package dev.sbs.discordapi.response.component.type;

import dev.sbs.discordapi.response.component.Component;
import org.jetbrains.annotations.NotNull;

public interface ToggleableComponent extends Component {

    @NotNull ToggleableComponent setState(boolean enabled);

}
