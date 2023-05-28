package dev.sbs.discordapi.listener.message.component;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.context.interaction.deferrable.component.ComponentContext;
import dev.sbs.discordapi.listener.DiscordListener;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.type.InteractableComponent;
import dev.sbs.discordapi.util.cache.ResponseCache;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class ComponentListener<E extends ComponentInteractionEvent, C extends ComponentContext, T extends InteractableComponent<C>> extends DiscordListener<E> {

    private final Class<T> componentClass;

    protected ComponentListener(DiscordBot discordBot) {
        super(discordBot);
        this.componentClass = Reflection.getSuperClass(this, 2);
    }

    /**
     * Handle paging and component interaction.
     * <br><br>
     * Override for specific components.
     *
     * @param event Discord4J instance of ComponentInteractionEvent.
     * @param responseCacheEntry Matched response cache entry.
     */
    protected Mono<C> handleEvent(@NotNull E event, @NotNull ResponseCache.Entry responseCacheEntry) {
        return Flux.fromIterable(responseCacheEntry.getResponse().getCachedPageComponents()) // Handle Paging Components
            .flatMap(layoutComponent -> Flux.fromIterable(layoutComponent.getComponents()))
            .filter(component -> event.getCustomId().equals(component.getIdentifier())) // Validate Component ID
            .filter(this.componentClass::isInstance) // Validate Component Type
            .map(this.componentClass::cast)
            .singleOrEmpty()
            .flatMap(component -> this.handlePagingInteraction(event, responseCacheEntry, component)) // Handle Response Paging
            .switchIfEmpty(
                Flux.fromIterable(responseCacheEntry.getResponse().getHistoryHandler().getCurrentPage().getComponents()) // Handle Component Interaction
                    .flatMap(layoutComponent -> Flux.fromIterable(layoutComponent.getComponents()))
                    .filter(component -> event.getCustomId().equals(component.getIdentifier())) // Validate Component ID
                    .filter(this.componentClass::isInstance) // Validate Component Type
                    .map(this.componentClass::cast)
                    .singleOrEmpty()
                    .flatMap(component -> this.handleInteraction(event, responseCacheEntry, component))
            );
    }

    @Override
    public final Publisher<Void> apply(@NotNull E componentEvent) {
        return Mono.just(componentEvent).flatMap(event -> Flux.fromIterable(this.getDiscordBot().getResponseCache())
            .filter(responseCacheEntry -> !event.getInteraction().getUser().isBot()) // Ignore Bots
            .filter(responseCacheEntry -> responseCacheEntry.getMessageId().equals(event.getMessageId())) // Validate Message ID
            .filter(responseCacheEntry -> responseCacheEntry.getUserId().equals(event.getInteraction().getUser().getId())) // Validate User ID
            .singleOrEmpty()
            .doOnNext(ResponseCache.Entry::setBusy)
            .flatMap(responseCacheEntry -> this.handleEvent(event, responseCacheEntry))
            .switchIfEmpty(event.deferEdit().then(Mono.empty())) // Invalid User Interaction
            .then()
        );
    }

    protected abstract C getContext(@NotNull E event, @NotNull Response cachedMessage, @NotNull T component);

    protected final Mono<C> handleInteraction(@NotNull E event, @NotNull ResponseCache.Entry responseCacheEntry, @NotNull T component) {
        return Mono.just(this.getContext(event, responseCacheEntry.getResponse(), component))
            .flatMap(context -> Mono.just(context)
                .onErrorResume(throwable -> this.getDiscordBot().handleException(
                    ExceptionContext.of(
                        this.getDiscordBot(),
                        context,
                        throwable,
                        FormatUtil.format("{0} Exception", this.getTitle())
                    )
                ))
                .then(component.isDeferEdit() ? context.deferEdit() : Mono.empty())
                .then(component.getInteraction().apply(context))
                .thenReturn(responseCacheEntry)
                .filter(ResponseCache.Entry::isModified)
                .flatMap(entry -> context.edit())
                .switchIfEmpty(Mono.fromRunnable(responseCacheEntry::updateLastInteract))
                .thenReturn(context)
            );
    }

    protected final Mono<C> handlePagingInteraction(@NotNull E event, @NotNull ResponseCache.Entry responseCacheEntry, @NotNull T component) {
        return Mono.just(this.getContext(event, responseCacheEntry.getResponse(), component))
            .flatMap(context -> Mono.just(context)
                .onErrorResume(throwable -> this.getDiscordBot().handleException(
                    ExceptionContext.of(
                        this.getDiscordBot(),
                        context,
                        throwable,
                        FormatUtil.format("{0} Paging Exception", this.getTitle())
                    )
                ))
                .then(context.deferEdit())
                .then(this.handlePaging(context))
                .thenReturn(responseCacheEntry)
                .filter(ResponseCache.Entry::isModified)
                .flatMap(entry -> context.edit())
                .switchIfEmpty(Mono.fromRunnable(responseCacheEntry::updateLastInteract))
                .thenReturn(context)
            );
    }

    protected abstract Mono<Void> handlePaging(@NotNull C context);

}
