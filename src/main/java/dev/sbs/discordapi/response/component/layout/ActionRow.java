package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.discordapi.response.component.action.ActionComponent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ActionRow<T extends ActionComponent<?>> extends LayoutComponent<T> {

    @Getter private final boolean preserved;

    @Override
    public discord4j.core.object.component.ActionRow getD4jComponent() {
        return discord4j.core.object.component.ActionRow.of(
            this.getComponents()
                .stream()
                .map(ActionComponent::getD4jComponent)
                .collect(Concurrent.toList())
        );
    }

    @SafeVarargs
    public static <T extends ActionComponent<?>> ActionRow<T> of(T... components) {
        return of(Arrays.asList(components));
    }

    public static <T extends ActionComponent<?>> ActionRow<T> of(Iterable<T> components) {
        return of(false, components);
    }

    @SafeVarargs
    public static <T extends ActionComponent<?>> ActionRow<T> preserved(T... components) {
        return preserved(Arrays.asList(components));
    }

    public static <T extends ActionComponent<?>> ActionRow<T> preserved(Iterable<T> components) {
        return of(true, components);
    }

    private static <T extends ActionComponent<?>> ActionRow<T> of(boolean preserved, Iterable<T> components) {
        ActionRow<T> actionRow = new ActionRow<>(preserved);
        components.forEach(component -> actionRow.getComponents().add(component));
        return actionRow;
    }
}
