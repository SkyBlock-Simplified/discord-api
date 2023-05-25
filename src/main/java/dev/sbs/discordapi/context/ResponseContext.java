package dev.sbs.discordapi.context;

import dev.sbs.discordapi.context.exception.ExceptionContext;
import dev.sbs.discordapi.context.message.MessageContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.util.cache.ResponseCache;
import discord4j.core.event.domain.Event;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;
import java.util.function.Function;

public interface ResponseContext<T extends Event> extends MessageContext<T> {

    Mono<Message> buildFollowup(@NotNull Response response);

    default Mono<Void> edit() {
        return Mono.justOrEmpty(this.getResponse()).flatMap(this::edit);
    }

    @Override
    default Mono<Void> edit(@NotNull Response response) {
        return this.editMessage(response)
            .checkpoint("ResponseContext#edit Processing")
            .onErrorResume(throwable -> this.getDiscordBot().handleException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    this,
                    throwable,
                    "Response Edit Exception"
                )
            ))
            .flatMap(message -> this.getDiscordBot().handleReactions(response, message))
            .then(this.withResponseCacheEntry(entry -> {
                entry.updateResponse(response);
                entry.setUpdated();
            }));
    }

    default Mono<Message> editMessage(@NotNull Response response) {
        return this.getMessage().flatMap(message -> message.edit(response.getD4jEditSpec()));
    }

    default Mono<Void> editPage(Function<Page.Builder, Page.Builder> currentPage) {
        return Mono.justOrEmpty(this.getResponse()).flatMap(response -> this.edit(
            response.mutate()
                .editPage(currentPage.apply(response.getHistoryHandler().getCurrentPage().mutate()).build())
                .build()
        ));
    }

    default Mono<Void> followup(@NotNull Response response) {
        return this.followup(response.getUniqueId().toString(), response);
    }

    default Mono<Void> followup(@NotNull String key, @NotNull Response response) {
        return this.buildFollowup(response)
            .flatMap(message -> this.getDiscordBot().handleReactions(response, message))
            .onErrorResume(throwable -> this.getDiscordBot().handleException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    this,
                    throwable,
                    "Followup Create Exception"
                )
            ))
            .flatMap(message -> this.withResponseCacheEntry(entry -> {
                entry.addFollowup(
                    key,
                    message.getChannelId(),
                    this.getInteractUserId(),
                    message.getId(),
                    response
                );

                entry.updateLastInteract();
                entry.setUpdated();
            }));
    }

    default @NotNull Response getResponse() {
        return this.getResponseCacheEntry().getResponse();
    }

    default @NotNull ResponseCache.Entry getResponseCacheEntry() {
        return this.getDiscordBot()
            .getResponseCache()
            .findFirstOrNull(entry -> entry.getResponse().getUniqueId(), this.getResponseId());
    }

    default Mono<Void> withResponseCacheEntry(@NotNull Consumer<ResponseCache.Entry> entry) {
        return Mono.just(this.getResponseCacheEntry())
            .doOnNext(entry)
            .then();
    }

}
