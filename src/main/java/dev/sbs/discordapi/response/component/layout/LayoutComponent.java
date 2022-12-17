package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;
import dev.sbs.discordapi.response.component.type.D4jComponent;
import dev.sbs.discordapi.response.component.type.PreservableComponent;
import lombok.Getter;

public abstract class LayoutComponent<T extends ActionComponent> extends Component implements PreservableComponent, D4jComponent {

    @Getter private final ConcurrentList<T> components = Concurrent.newList();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LayoutComponent<?> that = (LayoutComponent<?>) o;

        return new EqualsBuilder()
            .append(this.getComponents(), that.getComponents())
            .build();
    }

    public abstract discord4j.core.object.component.LayoutComponent getD4jComponent();

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getComponents())
            .build();
    }

}
