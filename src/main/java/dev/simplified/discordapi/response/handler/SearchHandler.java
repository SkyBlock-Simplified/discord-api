package dev.simplified.discordapi.response.handler;

import dev.simplified.collection.ConcurrentList;
import dev.simplified.discordapi.component.interaction.TextInput;
import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@EqualsAndHashCode
@Getter
@RequiredArgsConstructor
public class SearchHandler<T> implements OutputHandler<Search<T>> {

    private final @NotNull ConcurrentList<Search<T>> items;
    private @NotNull Optional<Search<T>> pending = Optional.empty();
    private boolean cacheUpdateRequired;

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
    public void setCacheUpdateRequired(boolean cacheUpdateRequired) {
        this.cacheUpdateRequired = cacheUpdateRequired;

        if (!cacheUpdateRequired)
            this.pending = Optional.empty();
    }

}
