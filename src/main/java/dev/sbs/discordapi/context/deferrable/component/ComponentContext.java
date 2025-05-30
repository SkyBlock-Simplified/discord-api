package dev.sbs.discordapi.context.deferrable.component;

import dev.sbs.discordapi.context.MessageContext;
import dev.sbs.discordapi.context.deferrable.DeferrableInteractionContext;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.Modal;
import dev.sbs.discordapi.response.component.type.UserInteractComponent;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.InteractionCallbackSpec;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

public interface ComponentContext extends MessageContext<ComponentInteractionEvent>, DeferrableInteractionContext<ComponentInteractionEvent> {

    @Override
    default Mono<Message> discordBuildFollowup(@NotNull Response response) {
        return this.deferReply(response.isEphemeral()).then(
            this.getEvent()
                .createFollowup(response.getD4jInteractionFollowupCreateSpec())
                .publishOn(response.getReactorScheduler())
        );
    }

    @Override
    default Mono<Void> discordDeleteFollowup(@NotNull String identifier) {
        return this.deferEdit().then(
            Mono.justOrEmpty(this.getFollowup(identifier))
                .flatMap(followup -> this.getEvent()
                    .deleteFollowup(followup.getMessageId())
                    .publishOn(followup.getResponse().getReactorScheduler())
                )
        );
    }

    @Override
    default Mono<Message> discordEditFollowup(@NotNull String identifier, @NotNull Response response) {
        return this.deferEdit(response.isEphemeral()).then(
            Mono.justOrEmpty(this.getFollowup(identifier))
            .flatMap(followup -> this.getEvent().editFollowup(followup.getMessageId(), response.getD4jInteractionReplyEditSpec()))
            .publishOn(response.getReactorScheduler())
        );
    }

    @Override
    default Mono<Message> discordEditMessage(@NotNull Response response) {
        return Mono.just(this.getResponseCacheEntry())
            .filter(CachedResponse::isDeferred)
            .flatMap(entry -> this.getEvent().editReply(response.getD4jInteractionReplyEditSpec()))
            .switchIfEmpty(
                this.getEvent()
                    .edit(response.getD4jComponentCallbackSpec())
                    .then(Mono.justOrEmpty(this.getEvent().getMessage()))
            )
            .publishOn(response.getReactorScheduler());
    }

    default Mono<Void> deferEdit() {
        return this.deferEdit(false);
    }

    default Mono<Void> deferEdit(boolean ephemeral) {
        return this.getEvent()
            .deferEdit(InteractionCallbackSpec.builder().ephemeral(ephemeral).build())
            .then(Mono.fromRunnable(() -> this.getResponseCacheEntry().setDeferred()));
    }

    @Override
    default Mono<MessageChannel> getChannel() {
        return DeferrableInteractionContext.super.getChannel();
    }

    @NotNull UserInteractComponent getComponent();

    @Override
    default Mono<Message> getMessage() {
        return Mono.justOrEmpty(this.getEvent().getMessage());
    }

    @Override
    default Snowflake getMessageId() {
        return this.getEvent().getMessageId();
    }

    default Mono<Void> presentModal(@NotNull Modal modal) {
        return Mono.justOrEmpty(this.getResponseCacheEntry())
            .doOnNext(entry -> entry.setUserModal(this.getInteractUser(), modal))
            .flatMap(entry -> this.getEvent().presentModal(modal.getD4jPresentSpec()));
    }

}