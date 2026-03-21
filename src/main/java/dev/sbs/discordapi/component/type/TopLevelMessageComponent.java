package dev.sbs.discordapi.component.type;

import dev.sbs.discordapi.component.Component;
import org.jetbrains.annotations.NotNull;

public interface TopLevelMessageComponent extends Component {

    @Override
    @NotNull discord4j.core.object.component.TopLevelMessageComponent getD4jComponent();

}
