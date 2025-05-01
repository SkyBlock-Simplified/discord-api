package dev.sbs.discordapi.response.handler.item.search;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.builder.hash.EqualsBuilder;
import dev.sbs.api.util.builder.hash.HashCodeBuilder;
import dev.sbs.discordapi.response.component.interaction.action.TextInput;
import dev.sbs.discordapi.response.handler.OutputHandler;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Getter
@RequiredArgsConstructor
public class SearchHandler<T> implements OutputHandler<Search<T>> {

    private final @NotNull ConcurrentList<Search<T>> items;
    private @NotNull Optional<Search<T>> pending = Optional.empty();
    private boolean cacheUpdateRequired;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        SearchHandler<?> that = (SearchHandler<?>) o;

        return new EqualsBuilder()
            .append(this.isCacheUpdateRequired(), that.isCacheUpdateRequired())
            .append(this.getPending(), that.getPending())
            .append(this.getItems(), that.getItems())
            .build();
    }

    public void search(@NotNull TextInput textInput) {
        if (this.notEmpty()) {
            this.pending = this.getItems()
                .stream()
                .filter(search -> search.getTextInput().getIdentifier().equals(textInput.getIdentifier()))
                .findFirst();

            if (this.getPending().isPresent()) {
                this.getPending().ifPresent(search -> search.updateLastMatch(textInput));
                this.setCacheUpdateRequired();
            }
        }
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getItems())
            .append(this.getPending())
            .append(this.isCacheUpdateRequired())
            .build();
    }

    @Override
    public void setCacheUpdateRequired(boolean cacheUpdateRequired) {
        this.cacheUpdateRequired = cacheUpdateRequired;

        if (!cacheUpdateRequired)
            this.pending = Optional.empty();
    }

}
