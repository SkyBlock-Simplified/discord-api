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
    public Publisher<Void> apply(E event) {
        return Flux.fromIterable(this.getDiscordBot().getResponseCache())
            .filter(responseCacheEntry -> !event.getInteraction().getUser().isBot()) // Ignore Bots
            .filter(responseCacheEntry -> responseCacheEntry.getMessageId().equals(event.getMessageId())) // Validate Message ID
            .filter(responseCacheEntry -> responseCacheEntry.getUserId().equals(event.getInteraction().getUser().getId())) // Validate User ID
            .switchIfEmpty(event.reply(
                Response.builder()
                    .withContent("You cannot interact with another users message!")
                    .build()
                    .getD4jComponentCallbackSpec()
                    .withEphemeral(true)
            ).then(Mono.empty()))
            .flatMap(responseCacheEntry -> Flux.fromIterable(responseCacheEntry.getResponse().getCurrentPage().getPageComponents())
                .flatMap(layoutComponent -> Flux.fromIterable(layoutComponent.getComponents()))
                .filter(this.componentClass::isInstance) // Validate Component Type
                .map(this.componentClass::cast)
                .filter(component -> event.getCustomId().equals(component.getUniqueId().toString())) // Validate Component ID
                .flatMap(component -> this.handlePagingInteraction(event, responseCacheEntry, component)) // Paging
                .switchIfEmpty(
                    Flux.fromIterable(responseCacheEntry.getResponse().getCurrentPage().getComponents())
                        .flatMap(layoutComponent -> Flux.fromIterable(layoutComponent.getComponents()))
                        .filter(this.componentClass::isInstance) // Validate Component Type
                        .map(this.componentClass::cast)
                        .filter(component -> event.getCustomId().equals(component.getUniqueId().toString())) // Validate Component ID
                        .flatMap(component -> this.handleInteraction(event, responseCacheEntry, component)) // Components
                )
                .switchIfEmpty(
                    Mono.just(responseCacheEntry.getResponse().getBackButton())
                        .filter(this.componentClass::isInstance) // Validate Component Type
                        .map(this.componentClass::cast)
                        .filter(component -> event.getCustomId().equals(component.getUniqueId().toString())) // Validate Component ID
                        .flatMap(component -> this.handlePagingInteraction(event, responseCacheEntry, component)) // Back Button
                )
            );
    }

    protected abstract C getContext(E event, Response cachedMessage, T component);

    private Mono<Void> handleInteraction(E event, DiscordResponseCache.Entry responseCacheEntry, T component) {
        return Mono.fromRunnable(() -> {
            responseCacheEntry.setBusy();
            C context = this.getContext(event, responseCacheEntry.getResponse(), component);

            try {
                component.getInteraction().accept(context);
            } catch (Exception uncaughtException) {
                this.getDiscordBot().handleUncaughtException(
                    ExceptionContext.of(
                        this.getDiscordBot(),
                        context,
                        uncaughtException,
                        FormatUtil.format("{0} Exception", this.getTitle())
                    )
                );
            }

            // Update Modified Cache Entries
            if (responseCacheEntry.isModified())
                context.edit();

            responseCacheEntry.updateLastInteract(); // Update TTL
        });
    }

    private Mono<Void> handlePagingInteraction(E event, DiscordResponseCache.Entry responseCacheEntry, T component) {
        return Mono.fromRunnable(() -> {
            responseCacheEntry.setBusy();
            C context = this.getContext(event, responseCacheEntry.getResponse(), component);

            try {
                this.handlePaging(context);
            } catch (Exception uncaughtException) {
                this.getDiscordBot().handleUncaughtException(
                    ExceptionContext.of(
                        this.getDiscordBot(),
                        context,
                        uncaughtException,
                        FormatUtil.format("{0} Exception", this.getTitle())
                    )
                );
            }

            // Update Modified Cache Entries
            if (responseCacheEntry.isModified())
                context.edit();

            responseCacheEntry.updateLastInteract(); // Update TTL
        });
    }

    protected abstract void handlePaging(C context);

}
