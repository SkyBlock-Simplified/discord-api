package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;
import dev.sbs.discordapi.response.component.type.AccessoryComponent;
import dev.sbs.discordapi.response.component.type.LabelComponent;
import dev.sbs.discordapi.response.component.type.TopLevelMessageComponent;
import dev.sbs.discordapi.response.component.type.UserInteractComponent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public interface LayoutComponent extends TopLevelMessageComponent {

    @NotNull ConcurrentList<Component> getComponents();

    @NotNull discord4j.core.object.component.LayoutComponent getD4jComponent();

    /**
     * Finds an existing {@link ActionComponent}.
     *
     * @param tClass   The component type to match.
     * @param function The method reference to match with.
     * @param value    The value to match with.
     * @return The matching component, if it exists.
     */
    default <S, T extends ActionComponent> @NotNull Optional<T> findComponent(@NotNull Class<T> tClass, @NotNull Function<T, S> function, S value) {
        return this.flattenComponents()
            .filter(tClass::isInstance)
            .map(tClass::cast)
            .filter(innerComponent -> Objects.equals(function.apply(innerComponent), value))
            .findFirst();
    }

    @Override
    default @NotNull Stream<Component> flattenComponents() {
        return Stream.concat(
            TopLevelMessageComponent.super.flattenComponents(),
            this.getComponents()
                .stream()
                .flatMap(Component::flattenComponents)
        );
    }

    /**
     * Modifies an existing {@link ActionComponent}.
     *
     * @param actionComponent The component to replace.
     */
    default <T extends ActionComponent> void modifyComponent(@NotNull T actionComponent) {
        this.getComponents().forEach(component -> {
            if (component instanceof LayoutComponent layoutComponent) {
                if (component instanceof Section section) {
                    if (section.getAccessory() instanceof UserInteractComponent userInteractComponent)
                        section.mutate().withAccessory((AccessoryComponent) actionComponent).build();
                } else
                    layoutComponent.modifyComponent(actionComponent);
            } else if (component instanceof Label label) {
                if (label.getComponent().getIdentifier().equals(actionComponent.getIdentifier()))
                    label.mutate().withComponent((LabelComponent) actionComponent).build();
            } else if (component instanceof ActionComponent innerComponent) {
                if (innerComponent.getIdentifier().equals(actionComponent.getIdentifier())) {
                    this.getComponents().set(
                        this.getComponents().indexOf(innerComponent),
                        actionComponent
                    );
                }
            }
        });
    }

}
