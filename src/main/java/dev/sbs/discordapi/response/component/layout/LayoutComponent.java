package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.action.ActionComponent;
import lombok.Getter;

public abstract class LayoutComponent<T extends ActionComponent<?>> extends Component {

    @Getter
    private final ConcurrentList<T> components = Concurrent.newList();

    @Override
    public abstract discord4j.core.object.component.LayoutComponent getD4jComponent();

}
