package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.discordapi.response.component.interaction.action.UserActionComponent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ActionRow extends LayoutComponent<UserActionComponent<?>> {

    @Getter private final boolean preserved;

    @Override
    public discord4j.core.object.component.ActionRow getD4jComponent() {
        return discord4j.core.object.component.ActionRow.of(
            this.getComponents()
                .stream()
                .map(UserActionComponent::getD4jComponent)
                .collect(Concurrent.toList())
        );
    }

    public static ActionRow of(@NotNull UserActionComponent<?>... components) {
        return of(Arrays.asList(components));
    }

    public static ActionRow of(@NotNull Iterable<UserActionComponent<?>> components) {
        return of(false, components);
    }

    public static ActionRow preserved(@NotNull UserActionComponent<?>... components) {
        return preserved(Arrays.asList(components));
    }

    public static ActionRow preserved(@NotNull Iterable<UserActionComponent<?>> components) {
        return of(true, components);
    }

    private static ActionRow of(boolean preserved, @NotNull Iterable<UserActionComponent<?>> components) {
        ActionRow actionRow = new ActionRow(preserved);
        components.forEach(component -> actionRow.getComponents().add(component));
        return actionRow;
    }
}
