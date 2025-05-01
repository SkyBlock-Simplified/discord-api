package dev.sbs.discordapi.response.handler.filter;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.response.handler.OutputHandler;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor
public class FilterHandler<T> implements OutputHandler<Filter<T>> {

    private final @NotNull ConcurrentList<Filter<T>> items;
    @Setter private boolean cacheUpdateRequired;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        FilterHandler<?> that = (FilterHandler<?>) o;

        return new EqualsBuilder()
            .append(this.isCacheUpdateRequired(), that.isCacheUpdateRequired())
            .append(this.getItems(), that.getItems())
            .build();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getItems())
            .append(this.isCacheUpdateRequired())
            .build();
    }

}
