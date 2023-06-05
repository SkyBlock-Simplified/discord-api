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

import java.util.Optional;

public abstract class ComponentListener<E extends ComponentInteractionEvent, C extends ComponentContext, T extends InteractableComponent<C>> extends DiscordListener<E> {

    private final Class<T> componentClass;

    protected ComponentListener(DiscordBot discordBot) {
        super(discordBot);
        this.componentClass = Reflection.getSuperClass(this, 2);
    }

    @Override
    public final Publisher<Void> apply(@NotNull E event) {
        return Flux.fromIterable(this.getDiscordBot().getResponseCache())
            .filter(entry -> !event.getInteraction().getUser().isBot()) // Ignore Bots
            .filter(entry -> entry.isMatch(entry.getMessageId(), event.getInteraction().getUser().getId())) // Validate Message & User ID
            .singleOrEmpty()
            .doOnNext(ResponseCache.Entry::setBusy)
            .flatMap(entry -> this.handleEvent(event, entry))
            .switchIfEmpty(
                Flux.fromIterable(this.getDiscordBot().getResponseCache())
                    .filter(entry -> !event.getInteraction().getUser().isBot()) // Ignore Bots
                    .filter(entry -> entry.getUserId().equals(event.getInteraction().getUser().getId())) // Validate User ID
                    .filter(entry -> entry.findFollowup(event.getMessageId()).isPresent()) // Validate Message ID
                    .singleOrEmpty()
                    .doOnNext(ResponseCache.Entry::setBusy)
                    .flatMap(entry -> this.handleFollowupEvent(event, entry, entry.findFollowup(event.getMessageId()).orElseThrow()))
                    .switchIfEmpty(event.deferEdit().then(Mono.empty())) // Invalid User Interaction
            )
            .then();
    }

    protected abstract C getContext(@NotNull E event, @NotNull Response cachedMessage, @NotNull T component, @NotNull Optional<ResponseCache.Followup> followup);

    /**
     * Handle paging and component interaction.
     * <br><br>
     * Override for specific components.
     *
     * @param event Discord4J instance of ComponentInteractionEvent.
     * @param entry Matched response cache entry.
     */
    protected Mono<C> handleEvent(@NotNull E event, @NotNull ResponseCache.Entry entry) {
        return Flux.fromIterable(entry.getResponse().getCachedPageComponents()) // Handle Paging Components
            .flatMap(layoutComponent -> Flux.fromIterable(layoutComponent.getComponents()))
            .filter(component -> event.getCustomId().equals(component.getIdentifier())) // Validate Component ID
            .filter(this.componentClass::isInstance) // Validate Component Type
            .map(this.componentClass::cast)
            .singleOrEmpty()
            .flatMap(component -> this.handlePagingInteraction(event, entry, component, Optional.empty())) // Handle Response Paging
            .switchIfEmpty(
                Flux.fromIterable(entry.getResponse().getHistoryHandler().getCurrentPage().getComponents()) // Handle Component Interaction
                    .flatMap(layoutComponent -> Flux.fromIterable(layoutComponent.getComponents()))
                    .filter(component -> event.getCustomId().equals(component.getIdentifier())) // Validate Component ID
                    .filter(this.componentClass::isInstance) // Validate Component Type
                    .map(this.componentClass::cast)
                    .singleOrEmpty()
                    .flatMap(component -> this.handleInteraction(event, entry, component, Optional.empty()))
            );
    }

    /**
     * Handle paging and component interaction for followups.
     * <br><br>
     * Override for specific components.
     *
     * @param event Discord4J instance of ComponentInteractionEvent.
     * @param entry Matched response cache entry.
     * @param followup Matched followup cache entry.
     */
    protected Mono<C> handleFollowupEvent(@NotNull E event, @NotNull ResponseCache.Entry entry, @NotNull ResponseCache.Followup followup) {
        return Flux.fromIterable(followup.getResponse().getCachedPageComponents()) // Handle Paging Components
            .flatMap(layoutComponent -> Flux.fromIterable(layoutComponent.getComponents()))
            .filter(component -> event.getCustomId().equals(component.getIdentifier())) // Validate Component ID
            .filter(this.componentClass::isInstance) // Validate Component Type
            .map(this.componentClass::cast)
            .singleOrEmpty()
            .flatMap(component -> this.handlePagingInteraction(event, entry, component, Optional.of(followup))) // Handle Response Paging
            .switchIfEmpty(
                Flux.fromIterable(followup.getResponse().getHistoryHandler().getCurrentPage().getComponents()) // Handle Component Interaction
                    .flatMap(layoutComponent -> Flux.fromIterable(layoutComponent.getComponents()))
                    .filter(component -> event.getCustomId().equals(component.getIdentifier())) // Validate Component ID
                    .filter(this.componentClass::isInstance) // Validate Component Type
                    .map(this.componentClass::cast)
                    .singleOrEmpty()
                    .flatMap(component -> this.handleInteraction(event, entry, component, Optional.of(followup)))
            );
    }

    protected final Mono<C> handleInteraction(@NotNull E event, @NotNull ResponseCache.Entry entry, @NotNull T component, @NotNull Optional<ResponseCache.Followup> followup) {
        return Mono.just(this.getContext(event, followup.map(ResponseCache.BaseEntry::getResponse).orElseGet(entry::getResponse), component, followup))
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
                .switchIfEmpty(
                    Mono.just(entry)
                        .filter(ResponseCache.Entry::isModified)
                        .flatMap(__ -> followup.isEmpty() ? context.edit() : context.editFollowup())
                )
                .doOnNext(__ -> entry.updateLastInteract())
                .thenReturn(context)
            );
    }

    protected final Mono<C> handlePagingInteraction(@NotNull E event, @NotNull ResponseCache.Entry entry, @NotNull T component, @NotNull Optional<ResponseCache.Followup> followup) {
        return Mono.just(this.getContext(event, followup.map(ResponseCache.BaseEntry::getResponse).orElseGet(entry::getResponse), component, followup))
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
                .switchIfEmpty(
                    Mono.just(entry)
                        .filter(ResponseCache.Entry::isModified)
                        .flatMap(__ -> followup.isEmpty() ? context.edit() : context.editFollowup())
                )
                .doOnNext(__ -> entry.updateLastInteract())
                .thenReturn(context)
            );
    }

    protected abstract Mono<Void> handlePaging(@NotNull C context);

}
