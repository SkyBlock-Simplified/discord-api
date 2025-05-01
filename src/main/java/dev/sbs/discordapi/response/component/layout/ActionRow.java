package dev.sbs.discordapi.response.component.layout;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.response.component.interaction.action.ActionComponent;
import dev.sbs.discordapi.response.component.type.v2.ContainerComponent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.UUID;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ActionRow implements LayoutComponent, ContainerComponent {

    private final @NotNull String identifier = UUID.randomUUID().toString();
    private final @NotNull ConcurrentList<ActionComponent> components;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ActionRow actionRow = (ActionRow) o;

        return new EqualsBuilder()
            .append(this.getIdentifier(), actionRow.getIdentifier())
            .append(this.getComponents(), actionRow.getComponents())
            .build();
    }

    @Override
    public @NotNull discord4j.core.object.component.ActionRow getD4jComponent() {
        return discord4j.core.object.component.ActionRow.of(
            this.getComponents()
                .stream()
                .map(ActionComponent::getD4jComponent)
                .collect(Concurrent.toList())
        );
    }

    @Override
    public @NotNull Type getType() {
        return Type.ACTION_ROW;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getIdentifier())
            .append(this.getComponents())
            .build();
    }

    public static @NotNull ActionRow of(@NotNull ActionComponent... components) {
        return of(Arrays.asList(components));
    }

    public static @NotNull ActionRow of(@NotNull Iterable<ActionComponent> components) {
        ConcurrentList<ActionComponent> componentList = Concurrent.newList();
        components.forEach(componentList::add);
        return new ActionRow(componentList.toUnmodifiableList());
    }

}
