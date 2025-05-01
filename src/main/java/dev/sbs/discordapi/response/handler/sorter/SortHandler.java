package dev.sbs.discordapi.response.handler.sorter;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.response.handler.OutputHandler;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Getter
public class SortHandler<T> implements OutputHandler<Sorter<T>> {

    private final @NotNull ConcurrentList<Sorter<T>> items;
    private int currentSorterIndex = -1;
    private boolean reversed = false;
    @Setter private boolean cacheUpdateRequired;

    public SortHandler(@NotNull ConcurrentList<Sorter<T>> items) {
        this.items = items;
        this.gotoNext();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        SortHandler<?> that = (SortHandler<?>) o;

        return new EqualsBuilder()
            .append(this.getCurrentSorterIndex(), that.getCurrentSorterIndex())
            .append(this.isReversed(), that.isReversed())
            .append(this.isCacheUpdateRequired(), that.isCacheUpdateRequired())
            .append(this.getItems(), that.getItems())
            .build();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getItems())
            .append(this.getCurrentSorterIndex())
            .append(this.isReversed())
            .append(this.isCacheUpdateRequired())
            .build();
    }

    public @NotNull Optional<Sorter<T>> getCurrent() {
        return Optional.ofNullable(this.getCurrentSorterIndex() > -1 ? this.getItems().get(this.getCurrentSorterIndex()) : null);
    }

    public void gotoNext() {
        if (this.notEmpty()) {
            this.currentSorterIndex++;

            if (this.currentSorterIndex >= this.getItems().size())
                this.currentSorterIndex = 0;

            this.setCacheUpdateRequired();
        }
    }

    public void invertOrder() {
        this.reversed = !this.isReversed();
        this.setCacheUpdateRequired();
    }

}
