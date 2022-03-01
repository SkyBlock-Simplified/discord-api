package dev.sbs.discordapi.context.message.interaction.component;

import dev.sbs.discordapi.context.message.interaction.ApplicationInteractionContext;
import dev.sbs.discordapi.context.message.interaction.UserInteractionContext;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.action.ActionComponent;
import dev.sbs.discordapi.util.DiscordResponseCache;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.InteractionCallbackSpec;
import reactor.core.publisher.Mono;

import java.util.Optional;

public interface ComponentContext extends UserInteractionContext<ComponentInteractionEvent>, ApplicationInteractionContext<ComponentInteractionEvent> {

    @Override
    default void edit(Response response) {
        this.getEvent()
            .edit(response.getD4jComponentCallbackSpec(this))
            .then(Mono.fromRunnable(() -> {
                DiscordResponseCache.Entry responseCacheEntry = this.getResponseCacheEntry();
                responseCacheEntry.updateResponse(response, true);
                responseCacheEntry.setUpdated();
            }))
            .block();
    }

    default Mono<Void> deferEdit() {
        return this.deferEdit(false);
    }

    default Mono<Void> deferEdit(boolean ephemeral) {
        return this.getEvent().deferEdit(InteractionCallbackSpec.builder().ephemeral(ephemeral).build());
    }

    @Override
    default Snowflake getChannelId() {
        return this.getEvent().getInteraction().getChannelId();
    }

    ActionComponent<?, ?> getComponent();

    @Override
    default Mono<Guild> getGuild() {
        return this.getEvent().getInteraction().getGuild();
    }

    @Override
    default Optional<Snowflake> getGuildId() {
        return this.getEvent().getInteraction().getGuildId();
    }

    @Override
    default Mono<User> getInteractUser() {
        return Mono.just(this.getEvent().getInteraction().getUser());
    }

    @Override
    default Snowflake getInteractUserId() {
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
    default Mono<Void> interactionReply(InteractionApplicationCommandCallbackSpec interactionApplicationCommandCallbackSpec) {
        return this.getEvent().reply(interactionApplicationCommandCallbackSpec);
    }

}