package dev.sbs.discordapi.component.layout;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.interaction.ActionComponent;
import dev.sbs.discordapi.component.type.AccessoryComponent;
import dev.sbs.discordapi.component.type.LabelComponent;
import dev.sbs.discordapi.component.type.TopLevelMessageComponent;
import dev.sbs.discordapi.component.type.UserInteractComponent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A top-level message component that contains child {@link Component Components}.
 *
 * <p>
 * Provides recursive traversal of the component tree via {@link #flattenComponents()},
 * targeted lookup via {@link #findComponent(Class, Function, Object)}, and in-place
 * replacement via {@link #modifyComponent(ActionComponent)}.
 */
public interface LayoutComponent extends TopLevelMessageComponent {

    /** The child components held by this layout. */
    @NotNull ConcurrentList<Component> getComponents();

    /** {@inheritDoc} */
    @NotNull discord4j.core.object.component.LayoutComponent getD4jComponent();

    /**
     * Finds the first {@link ActionComponent} of the given type whose extracted property
     * matches the specified value.
     *
     * @param tClass the action component subtype to search for
     * @param function the property extractor applied to each candidate
     * @param value the value to match against the extracted property
     * @param <S> the property type
     * @param <T> the action component subtype
     * @return an {@link Optional} containing the matching component, or empty if none is found
     */
    default <S, T extends ActionComponent> @NotNull Optional<T> findComponent(@NotNull Class<T> tClass, @NotNull Function<T, S> function, S value) {
        return this.flattenComponents()
            .filter(tClass::isInstance)
            .map(tClass::cast)
            .filter(innerComponent -> Objects.equals(function.apply(innerComponent), value))
            .findFirst();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Recursively flattens this layout and all nested child components into a single stream.
     */
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
     * Replaces an existing {@link ActionComponent} in the component tree with the given
     * replacement, matched by {@linkplain UserInteractComponent#getIdentifier() identifier}.
     *
     * <p>
     * The replacement walks the tree recursively through nested {@link LayoutComponent}
     * instances, {@link Section} accessories, and {@link Label} wrapped components.
     *
     * @param actionComponent the replacement component whose identifier determines which
     *                        existing component is replaced
     * @param <T> the action component subtype
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
