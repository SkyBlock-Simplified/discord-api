package dev.sbs.discordapi.context.interaction.deferrable.component;

import dev.sbs.discordapi.context.ResponseContext;
import dev.sbs.discordapi.context.interaction.deferrable.DeferrableInteractionContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.Component;
import dev.sbs.discordapi.response.component.interaction.Modal;
import dev.sbs.discordapi.util.cache.ResponseCache;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.InteractionCallbackSpec;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;

public interface ComponentContext extends ResponseContext<ComponentInteractionEvent>, DeferrableInteractionContext<ComponentInteractionEvent> {

    @Override
    default Mono<Message> discordBuildFollowup(@NotNull Response response) {
        return this.getEvent()
            .createFollowup(response.getD4jInteractionFollowupCreateSpec())
            .publishOn(response.getReactorScheduler());
    }

    @Override
    default Mono<Void> discordDeleteFollowup(@NotNull String identifier) {
        return Mono.justOrEmpty(this.getFollowup(identifier)).flatMap(followup -> this.getEvent().deleteFollowup(followup.getMessageId()));
    }

    @Override
    default Mono<Message> discordEditFollowup(@NotNull String identifier, @NotNull Response response) {
        return Mono.justOrEmpty(this.getFollowup(identifier))
            .flatMap(followup -> this.getEvent().editFollowup(followup.getMessageId(), response.getD4jInteractionReplyEditSpec()))
            .publishOn(response.getReactorScheduler());
    }

    @Override
    default Mono<Message> discordEditMessage(@NotNull Response response) {
        return Mono.just(this.getResponseCacheEntry())
            .filter(ResponseCache.Entry::isDeferred)
            .flatMap(entry -> this.getEvent().editReply(response.getD4jInteractionReplyEditSpec()))
            .switchIfEmpty(
                this.getEvent()
                    .edit(response.getD4jComponentCallbackSpec())
                    .then(Mono.justOrEmpty(this.getEvent().getMessage()))
            );
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
    default @NotNull Snowflake getChannelId() {
        return this.getEvent().getInteraction().getChannelId();
    }

    @NotNull Component getComponent();

    @Override
    default Mono<Guild> getGuild() {
        return this.getEvent().getInteraction().getGuild();
    }

    @Override
    default Optional<Snowflake> getGuildId() {
        return this.getEvent().getInteraction().getGuildId();
    }

    @Override
    default @NotNull User getInteractUser() {
        return this.getEvent().getInteraction().getUser();
    }

    @Override
    default @NotNull Snowflake getInteractUserId() {
        return this.getEvent().getInteraction().getUser().getId();
    }

    @Override
    default Mono<Message> getMessage() {
        return Mono.justOrEmpty(this.getEvent().getMessage());
    }

    @Override
    default Snowflake getMessageId() {
        return this.getEvent().getMessageId();
    }

    @Override
    default Mono<Message> getReply() {
        return this.getEvent().getReply();
    }

    @Override
    default Mono<Void> interactionEdit(InteractionApplicationCommandCallbackSpec interactionApplicationCommandCallbackSpec) {
        return this.getEvent().edit(interactionApplicationCommandCallbackSpec);
    }

    default Mono<Void> presentModal(@NotNull Modal modal) {
        return Mono.justOrEmpty(this.getResponseCacheEntry())
            .doOnNext(entry -> entry.setActiveModal(modal))
            .flatMap(entry -> this.getEvent().presentModal(modal.getD4jPresentSpec()));
    }

}