package dev.sbs.discordapi.listener.message.component;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.helper.FormatUtil;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.context.message.interaction.component.ComponentContext;
import dev.sbs.discordapi.listener.DiscordListener;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.action.ActionComponent;
import dev.sbs.discordapi.util.DiscordResponseCache;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class ComponentListener<E extends ComponentInteractionEvent, C extends ComponentContext, T extends ActionComponent<C, ?>> extends DiscordListener<E> {

    private final Class<T> componentClass;

    protected ComponentListener(DiscordBot discordBot) {
        super(discordBot);
        this.componentClass = Reflection.getSuperClass(this, 2);
    }

    @Override
    public Publisher<Void> apply(E componentEvent) {
        return Mono.just(componentEvent).flatMap(event -> Flux.fromIterable(this.getDiscordBot().getResponseCache())
            .filter(responseCacheEntry -> !event.getInteraction().getUser().isBot()) // Ignore Bots
            .filter(responseCacheEntry -> responseCacheEntry.getMessageId().equals(event.getMessageId())) // Validate Message ID
            .filter(responseCacheEntry -> responseCacheEntry.getUserId().equals(event.getInteraction().getUser().getId())) // Validate User ID
            .switchIfEmpty(event.deferEdit().then(Mono.empty())) // Invalid User Interaction
            .flatMap(responseCacheEntry -> Flux.fromIterable(responseCacheEntry.getResponse().getPageComponents())
                .flatMap(layoutComponent -> Flux.fromIterable(layoutComponent.getComponents()))
                .filter(this.componentClass::isInstance) // Validate Component Type
                .map(this.componentClass::cast)
                .filter(component -> event.getCustomId().equals(component.getUniqueId().toString())) // Validate Component ID
                .singleOrEmpty()
                .flatMap(component -> this.handlePagingInteraction(event, responseCacheEntry, component)) // Handle Response Paging
                .switchIfEmpty(
                    Flux.fromIterable(responseCacheEntry.getResponse().getCurrentPage().getPageComponents())
                        .flatMap(layoutComponent -> Flux.fromIterable(layoutComponent.getComponents()))
                        .filter(this.componentClass::isInstance) // Validate Component Type
                        .map(this.componentClass::cast)
                        .filter(component -> event.getCustomId().equals(component.getUniqueId().toString())) // Validate Component ID
                        .singleOrEmpty()
                        .flatMap(component -> this.handlePagingInteraction(event, responseCacheEntry, component)) // Handle SubPage Paging
                        .switchIfEmpty(
                            Flux.fromIterable(responseCacheEntry.getResponse().getCurrentPage().getComponents())
                                .flatMap(layoutComponent -> Flux.fromIterable(layoutComponent.getComponents()))
                                .filter(this.componentClass::isInstance) // Validate Component Type
                                .map(this.componentClass::cast)
                                .filter(component -> event.getCustomId().equals(component.getUniqueId().toString())) // Validate Component ID
                                .singleOrEmpty()
                                .flatMap(component -> this.handleInteraction(event, responseCacheEntry, component)) // Handle Interaction
                                .switchIfEmpty(
                                    Mono.just(responseCacheEntry.getResponse().getBackButton())
                                        .filter(this.componentClass::isInstance) // Validate Component Type
                                        .map(this.componentClass::cast)
                                        .filter(component -> event.getCustomId().equals(component.getUniqueId().toString())) // Validate Component ID
                                        .flatMap(component -> this.handlePagingInteraction(event, responseCacheEntry, component)) // Handle Back Button
                                )
                        )
                )
            )
            .then()
        );
    }

    protected abstract C getContext(E event, Response cachedMessage, T component);

    private Mono<Void> handleInteraction(E event, DiscordResponseCache.Entry responseCacheEntry, T component) {
        return Mono.just(this.getContext(event, responseCacheEntry.getResponse(), component))
            .flatMap(context -> Mono.just(responseCacheEntry)
                .onErrorResume(throwable -> this.getDiscordBot().handleUncaughtException(
                    ExceptionContext.of(
                        this.getDiscordBot(),
                        context,
                        throwable,
                        FormatUtil.format("{0} Exception", this.getTitle())
                    )
                ))
                .doOnNext(DiscordResponseCache.Entry::setBusy)
                .flatMap(entry -> component.isDeferEdit() ? context.deferEdit() : Mono.just(entry))
                .flatMap(__ -> component.getInteraction().apply(context))
                .thenReturn(responseCacheEntry)
                .doOnNext(DiscordResponseCache.Entry::updateLastInteract)
                .filter(DiscordResponseCache.Entry::isModified)
                .flatMap(entry -> context.edit())
            );
    }

    private Mono<Void> handlePagingInteraction(E event, DiscordResponseCache.Entry responseCacheEntry, T component) {
        return Mono.just(this.getContext(event, responseCacheEntry.getResponse(), component))
            .flatMap(context -> Mono.just(responseCacheEntry)
                .onErrorResume(throwable -> this.getDiscordBot().handleUncaughtException(
                    ExceptionContext.of(
                        this.getDiscordBot(),
                        context,
                        throwable,
                        FormatUtil.format("{0} Exception", this.getTitle())
                    )
                ))
                .doOnNext(DiscordResponseCache.Entry::setBusy)
                .flatMap(entry -> this.handlePaging(context))
                .then(
                    Mono.just(responseCacheEntry)
                        .doOnNext(DiscordResponseCache.Entry::updateLastInteract)
                        .filter(DiscordResponseCache.Entry::isModified)
                        .flatMap(entry -> context.edit())
                )
            );
    }

    protected abstract Mono<Void> handlePaging(C context);

}
