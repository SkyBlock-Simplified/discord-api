package dev.sbs.discordapi.response.page.handler;

import dev.sbs.api.util.SimplifiedException;
import dev.sbs.api.util.builder.EqualsBuilder;
import dev.sbs.api.util.builder.hashcode.HashCodeBuilder;
import dev.sbs.api.util.collection.concurrent.Concurrent;
import dev.sbs.api.util.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.helper.ListUtil;
import dev.sbs.discordapi.util.exception.DiscordException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Handler<P extends Paging<P>, I> implements Paging<P> {

    @Getter private final ConcurrentList<P> pages;
    @Getter private final Optional<Function<P, I>> historyTransformer;
    @Getter private final Optional<BiFunction<P, I, Boolean>> historyMatcher;
    @Getter private boolean cacheUpdateRequired = false;
    protected final ConcurrentList<P> history = Concurrent.newList();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Handler<?, ?> handler = (Handler<?, ?>) o;

        return new EqualsBuilder()
            .append(this.isCacheUpdateRequired(), handler.isCacheUpdateRequired())
            .append(this.getPages(), handler.getPages())
            .append(this.getHistoryTransformer(), handler.getHistoryTransformer())
            .append(this.getHistoryMatcher(), handler.getHistoryMatcher())
            .append(this.getHistory(), handler.getHistory())
            .build();
    }

    public final P getCurrentPage() {
        return this.history.getLast().orElseThrow(); // Will Always Exist
    }

    public final Optional<P> getPreviousPage() {
        return Optional.ofNullable(this.history.size() > 1 ? this.history.get(this.history.size() - 2) : null);
    }

    public final ConcurrentList<P> getHistory() {
        return this.history.toUnmodifiableList();
    }

    public final ConcurrentList<I> getHistoryIdentifiers() {
        return this.history.stream()
            .map(page -> this.getHistoryTransformer().map(transformer -> transformer.apply(page)))
            .flatMap(Optional::stream)
            .collect(Concurrent.toList());
    }

    /**
     * Gets a {@link P subpage} from the {@link P CurrentPage}.
     *
     * @param identifier The subpage option value.
     */
    public final Optional<P> getSubPage(I identifier) {
        return this.getCurrentPage()
            .getPages()
            .stream()
            .filter(page -> this.getHistoryMatcher().map(matcher -> matcher.apply(page, identifier)).orElse(false))
            .findFirst();
    }

    public final void gotoPreviousPage() {
        if (this.hasPageHistory())
            this.history.removeLast();
    }

    /**
     * Changes the current {@link P page} to a subpage of the current {@link P page} using the given identifier.
     *
     * @param identifier The subpage option value.
     */
    public final void gotoSubPage(I identifier) {
        this.history.add(this.getSubPage(identifier).orElseThrow(
            () -> SimplifiedException.of(DiscordException.class)
                .withMessage("Unable to locate subpage identified by ''{0}''!", identifier)
                .build()
        ));
        this.setCacheUpdateRequired();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getPages())
            .append(this.getHistoryTransformer())
            .append(this.getHistoryMatcher())
            .append(this.isCacheUpdateRequired())
            .append(this.getHistory())
            .build();
    }

    public final boolean hasPageHistory() {
        return ListUtil.sizeOf(this.history) > 1;
    }

    public final void setCacheUpdateRequired() {
        this.setCacheUpdateRequired(true);
    }

    public final void setCacheUpdateRequired(boolean value) {
        this.cacheUpdateRequired = value;
    }

}
