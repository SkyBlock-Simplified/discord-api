package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.response.component.type.D4jComponent;
import dev.sbs.discordapi.response.component.type.IdentifiableComponent;
import dev.sbs.discordapi.response.component.type.PreservableComponent;
import org.jetbrains.annotations.NotNull;

public interface LayoutComponent<T extends IdentifiableComponent> extends PreservableComponent, D4jComponent {

    @NotNull ConcurrentList<T> getComponents();

    @NotNull discord4j.core.object.component.LayoutComponent getD4jComponent();

}
