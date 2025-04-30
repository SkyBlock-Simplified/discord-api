package dev.sbs.discordapi.listener.deferrable.component;

import dev.sbs.api.reflection.Reflection;
import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.deferrable.component.ComponentContext;
import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.listener.DiscordListener;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.type.EventComponent;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

public abstract class ComponentListener<E extends ComponentInteractionEvent, C extends ComponentContext, T extends EventComponent<C>> extends DiscordListener<E> {

    private final Class<T> componentClass;

    protected ComponentListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
        this.componentClass = Reflection.getSuperClass(this, 2);
    }

    @Override
    public final Publisher<Void> apply(@NotNull E event) {
        return Flux.fromIterable(this.getDiscordBot().getResponseHandler())
            .filter(entry -> !event.getInteraction().getUser().isBot()) // Ignore Bots
            .filter(entry -> entry.matchesMessage(event.getMessageId(), event.getInteraction().getUser().getId())) // Validate Message & User ID
            .singleOrEmpty()
            .switchIfEmpty(event.deferEdit().then(Mono.empty())) // Invalid User Interaction
            .doOnNext(CachedResponse::setBusy)
            .flatMap(entry -> this.handleEvent(event, entry, entry.findFollowup(event.getMessageId())));
    }

    protected abstract @NotNull C getContext(@NotNull E event, @NotNull Response cachedMessage, @NotNull T component, @NotNull Optional<Followup> followup);

    /**
     * Finds interacted component to pass to {@link #handleInteraction}.
     * <br><br>
     * Override for special handling.
     *
     * @param event Discord4J instance of ComponentInteractionEvent.
     * @param entry Matched response cache entry.
     * @param followup Matched followup cache entry.
     */
    protected Mono<Void> handleEvent(@NotNull E event, @NotNull CachedResponse entry, @NotNull Optional<Followup> followup) {
        return Flux.fromIterable((followup.isPresent() ? followup.get() : entry).getResponse().getCachedPageComponents())
            .concatWith(Flux.fromIterable((followup.isPresent() ? followup.get() : entry).getResponse().getHistoryHandler().getCurrentPage().getComponents()))
            .flatMap(layoutComponent -> Flux.fromIterable(layoutComponent.getComponents()))
            .filter(component -> event.getCustomId().equals(component.getIdentifier())) // Validate Component ID
            .filter(this.componentClass::isInstance) // Validate Component Type
            .map(this.componentClass::cast)
            .singleOrEmpty()
            .flatMap(component -> this.handleInteraction(event, entry, component, followup))
            .then(entry.updateLastInteract())
            .then();
    }

    protected final Mono<Void> handleInteraction(@NotNull E event, @NotNull CachedResponse entry, @NotNull T component, @NotNull Optional<Followup> followup) {
        C context = this.getContext(event, entry.getResponse(), component, followup);

        return Mono.just(context)
            .then(component.isDeferEdit() ? context.deferEdit() : Mono.empty())
            .then(Mono.defer(() -> component.getInteraction().apply(context)))
            .checkpoint("ComponentListener#handleInteraction Processing")
            .onErrorResume(throwable -> context.deferEdit().then(
                this.getDiscordBot().getExceptionHandler().handleException(
                    ExceptionContext.of(
                        this.getDiscordBot(),
                        context,
                        throwable,
                        String.format("%s Exception", this.getTitle())
                    )
                )
            ))
            .switchIfEmpty(
                Mono.just(entry)
                    .filter(CachedResponse::isModified)
                    .flatMap(__ -> followup.isEmpty() ? context.edit() : context.editFollowup())
            );
    }

}
