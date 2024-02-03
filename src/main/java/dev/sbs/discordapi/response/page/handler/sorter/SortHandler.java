package dev.sbs.discordapi.response.page.handler.sorter;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.response.page.handler.cache.CacheHandler;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Getter
public class SortHandler<T> implements CacheHandler {

    private final @NotNull ConcurrentList<Sorter<T>> sorters;
    private int currentSorterIndex = -1;
    private boolean reversed = false;
    @Setter private boolean cacheUpdateRequired;

    public SortHandler(@NotNull ConcurrentList<Sorter<T>> sorters) {
        this.sorters = sorters;
        this.gotoNext();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SortHandler<?> that = (SortHandler<?>) o;

        return new EqualsBuilder()
            .append(this.getCurrentSorterIndex(), that.getCurrentSorterIndex())
            .append(this.isReversed(), that.isReversed())
            .append(this.isCacheUpdateRequired(), that.isCacheUpdateRequired())
            .append(this.getSorters(), that.getSorters())
            .build();
    }

    public @NotNull Optional<Sorter<T>> getCurrent() {
        return Optional.ofNullable(this.getCurrentSorterIndex() > -1 ? this.getSorters().get(this.getCurrentSorterIndex()) : null);
    }

    public void gotoNext() {
        if (this.hasSorters()) {
            this.currentSorterIndex++;

            if (this.currentSorterIndex >= this.getSorters().size())
                this.currentSorterIndex = 0;

            this.setCacheUpdateRequired();
        }
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getSorters())
            .append(this.getCurrentSorterIndex())
            .append(this.isReversed())
            .append(this.isCacheUpdateRequired())
            .build();
    }

    public boolean hasSorters() {
        return this.getSorters().notEmpty();
    }

    public void invertOrder() {
        this.reversed = !this.isReversed();
        this.setCacheUpdateRequired();
    }

}
