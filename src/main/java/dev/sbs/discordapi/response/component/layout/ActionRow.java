package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ActionRow extends LayoutComponent<ActionComponent> {

    private final boolean preserved;

    @Override
    public @NotNull discord4j.core.object.component.ActionRow getD4jComponent() {
        return discord4j.core.object.component.ActionRow.of(
            this.components.stream()
                .map(ActionComponent::getD4jComponent)
                .collect(Concurrent.toList())
        );
    }

    public static ActionRow of(@NotNull ActionComponent... components) {
        return of(Arrays.asList(components));
    }

    public static ActionRow of(@NotNull Iterable<ActionComponent> components) {
        return of(components, false);
    }

    public static ActionRow preserved(@NotNull ActionComponent... components) {
        return preserved(Arrays.asList(components));
    }

    public static ActionRow preserved(@NotNull Iterable<ActionComponent> components) {
        return of(components, true);
    }

    private static ActionRow of(@NotNull Iterable<ActionComponent> components, boolean preserved) {
        ActionRow actionRow = new ActionRow(preserved);
        components.forEach(actionRow.components::add);
        return actionRow;
    }
}
