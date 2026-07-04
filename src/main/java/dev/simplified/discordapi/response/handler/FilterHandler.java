package dev.simplified.discordapi.response.handler;

import dev.simplified.collection.ConcurrentList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;

@Getter
@RequiredArgsConstructor
public class FilterHandler<T> implements OutputHandler<Filter<T>> {

    private final @NotNull ConcurrentList<Filter<T>> items;
    @Setter private boolean cacheUpdateRequired;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        FilterHandler<?> that = (FilterHandler<?>) o;

        return this.isCacheUpdateRequired() == that.isCacheUpdateRequired()
            && Objects.equals(this.getItems(), that.getItems());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getItems(), this.isCacheUpdateRequired());
    }

    /**
     * Enables exactly the filters whose identifiers appear in the given set and disables the
     * rest, marking the cache stale so the item pipeline re-filters on the next render.
     *
     * @param enabledIdentifiers the identifiers of the filters to enable
     */
    public void applyEnabled(@NotNull Set<String> enabledIdentifiers) {
        ConcurrentList<Filter<T>> current = this.getItems();

        for (int index = 0; index < current.size(); index++) {
            Filter<T> filter = current.get(index);
            boolean enable = enabledIdentifiers.contains(filter.getIdentifier());

            if (filter.isEnabled() != enable)
                current.set(index, filter.mutate().isEnabled(enable).build());
        }

        this.setCacheUpdateRequired();
    }

}
