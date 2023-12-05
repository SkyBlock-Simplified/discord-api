package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.response.component.type.D4jComponent;
import dev.sbs.discordapi.response.component.type.IdentifiableComponent;
import dev.sbs.discordapi.response.component.type.PreservableComponent;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
public abstract class LayoutComponent<T extends IdentifiableComponent> implements PreservableComponent, D4jComponent {

    protected final @NotNull ConcurrentList<T> components = Concurrent.newList();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LayoutComponent<?> that = (LayoutComponent<?>) o;

        return new EqualsBuilder()
            .append(this.components, that.components)
            .build();
    }

    public abstract @NotNull discord4j.core.object.component.LayoutComponent getD4jComponent();

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.components)
            .build();
    }

}
