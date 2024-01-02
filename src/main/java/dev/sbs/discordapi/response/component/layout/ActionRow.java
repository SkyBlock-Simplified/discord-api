package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ActionRow implements LayoutComponent<ActionComponent> {

    private final @NotNull ConcurrentList<ActionComponent> components = Concurrent.newList();
    private final boolean preserved;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ActionRow actionRow = (ActionRow) o;

        return new EqualsBuilder()
            .append(this.isPreserved(), actionRow.isPreserved())
            .append(this.getComponents(), actionRow.getComponents())
            .build();
    }

    @Override
    public @NotNull discord4j.core.object.component.ActionRow getD4jComponent() {
        return discord4j.core.object.component.ActionRow.of(
            this.components.stream()
                .map(ActionComponent::getD4jComponent)
                .collect(Concurrent.toList())
        );
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getComponents())
            .append(this.isPreserved())
            .build();
    }

    public static @NotNull ActionRow of(@NotNull ActionComponent... components) {
        return of(Arrays.asList(components));
    }

    public static @NotNull ActionRow of(@NotNull Iterable<ActionComponent> components) {
        return of(components, false);
    }

    public static @NotNull ActionRow preserved(@NotNull ActionComponent... components) {
        return preserved(Arrays.asList(components));
    }

    public static @NotNull ActionRow preserved(@NotNull Iterable<ActionComponent> components) {
        return of(components, true);
    }

    private static @NotNull ActionRow of(@NotNull Iterable<ActionComponent> components, boolean preserved) {
        ActionRow actionRow = new ActionRow(preserved);
        components.forEach(actionRow.components::add);
        return actionRow;
    }

}
