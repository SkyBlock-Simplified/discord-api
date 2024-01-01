package dev.sbs.discordapi.response.page.handler.sorter;

import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.discordapi.response.page.handler.CacheHandler;
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

    public @NotNull Optional<Sorter<T>> getCurrent() {
        return Optional.ofNullable(this.getCurrentSorterIndex() > -1 ? this.getSorters().get(this.getCurrentSorterIndex()) : null);
    }

    public void gotoNext() {
        if (this.hasSorters()) {
            this.currentSorterIndex++;

            if (this.currentSorterIndex >= ListUtil.sizeOf(this.getSorters()))
                this.currentSorterIndex = 0;

            this.setCacheUpdateRequired();
        }
    }

    public boolean hasSorters() {
        return this.getSorters().notEmpty();
    }

    public void invertOrder() {
        this.reversed = !this.isReversed();
        this.setCacheUpdateRequired();
    }

}
