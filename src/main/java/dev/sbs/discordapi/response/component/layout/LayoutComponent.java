package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.response.component.Component;
import lombok.Getter;

public abstract class LayoutComponent<T extends Component> extends Component {

    @Getter
    private final ConcurrentList<T> components = Concurrent.newList();

    @Override
    public abstract discord4j.core.object.component.LayoutComponent getD4jComponent();

}
