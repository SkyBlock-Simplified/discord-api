package dev.sbs.discordapi.response.page.handler.filter;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.response.page.handler.cache.CacheHandler;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Getter
public class FilterHandler<T> implements CacheHandler {

    private final @NotNull ConcurrentList<Filter<T>> filters;
    private int currentSorterIndex = -1;
    private boolean reversed = false;
    @Setter private boolean cacheUpdateRequired;

    public FilterHandler(@NotNull ConcurrentList<Filter<T>> filters) {
        this.filters = filters;
        this.gotoNext();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FilterHandler<?> that = (FilterHandler<?>) o;

        return new EqualsBuilder()
            .append(this.getCurrentSorterIndex(), that.getCurrentSorterIndex())
            .append(this.isReversed(), that.isReversed())
            .append(this.isCacheUpdateRequired(), that.isCacheUpdateRequired())
            .append(this.getFilters(), that.getFilters())
            .build();
    }

    public @NotNull Optional<Filter<T>> getCurrent() {
        return Optional.ofNullable(this.getCurrentSorterIndex() > -1 ? this.getFilters().get(this.getCurrentSorterIndex()) : null);
    }

    public void gotoNext() {
        if (this.hasSorters()) {
            this.currentSorterIndex++;

            if (this.currentSorterIndex >= this.getFilters().size())
                this.currentSorterIndex = 0;

            this.setCacheUpdateRequired();
        }
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getFilters())
            .append(this.getCurrentSorterIndex())
            .append(this.isReversed())
            .append(this.isCacheUpdateRequired())
            .build();
    }

    public boolean hasSorters() {
        return this.getFilters().notEmpty();
    }

    public void invertOrder() {
        this.reversed = !this.isReversed();
        this.setCacheUpdateRequired();
    }

}
