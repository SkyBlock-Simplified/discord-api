package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.discordapi.response.component.action.ActionComponent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ActionRow extends LayoutComponent<ActionComponent<?>> {

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

    public static ActionRow of(@NotNull ActionComponent<?>... components) {
        return of(Arrays.asList(components));
    }

    public static ActionRow of(@NotNull Iterable<ActionComponent<?>> components) {
        return of(false, components);
    }

    public static ActionRow preserved(@NotNull ActionComponent<?>... components) {
        return preserved(Arrays.asList(components));
    }

    public static ActionRow preserved(@NotNull Iterable<ActionComponent<?>> components) {
        return of(true, components);
    }

    private static ActionRow of(boolean preserved, @NotNull Iterable<ActionComponent<?>> components) {
        ActionRow actionRow = new ActionRow(preserved);
        components.forEach(component -> actionRow.getComponents().add(component));
        return actionRow;
    }
}
