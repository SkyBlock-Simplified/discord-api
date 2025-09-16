package dev.sbs.discordapi.response.component.type;

import dev.sbs.discordapi.response.component.Component;
import org.jetbrains.annotations.NotNull;

public interface TopLevelModalComponent extends Component {

    @Override
    @NotNull discord4j.core.object.component.TopLevelModalComponent getD4jComponent();

}
