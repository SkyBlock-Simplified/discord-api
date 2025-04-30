package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.response.component.Component;
import org.jetbrains.annotations.NotNull;

public interface LayoutComponent extends Component {

    @NotNull ConcurrentList<Component> getComponents();

    @NotNull discord4j.core.object.component.LayoutComponent getD4jComponent();

}
