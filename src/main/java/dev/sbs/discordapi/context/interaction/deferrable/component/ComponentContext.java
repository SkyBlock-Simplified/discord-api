package dev.sbs.discordapi.context.interaction.deferrable.component;

import dev.sbs.discordapi.context.ResponseContext;
import dev.sbs.discordapi.context.interaction.deferrable.DeferrableInteractionContext;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.InteractionCallbackSpec;
import reactor.core.publisher.Mono;

import java.util.Optional;

public interface ComponentContext extends ResponseContext<ComponentInteractionEvent>, DeferrableInteractionContext<ComponentInteractionEvent> {

    @Override
    default Mono<Message> editMessage(Response response) {
        return this.getEvent()
            .edit(response.getD4jComponentCallbackSpec())
            .then(Mono.justOrEmpty(this.getEvent().getMessage()));
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

    @Override
    default Mono<Guild> getGuild() {
        return this.getEvent().getInteraction().getGuild();
    }

    @Override
    default Optional<Snowflake> getGuildId() {
        return this.getEvent().getInteraction().getGuildId();
    }

    @Override
    default User getInteractUser() {
        return this.getEvent().getInteraction().getUser();
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