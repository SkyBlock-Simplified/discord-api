package dev.sbs.discordapi.context.message.interaction;

import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import reactor.core.publisher.Mono;

public interface ApplicationInteractionContext<T extends InteractionCreateEvent> extends EventContext<T> {

    @Override
    default Mono<Message> buildMessage(Response response) {
        return this.interactionReply(response.getD4jComponentCallbackSpec(this))
            .publishOn(response.getReactorScheduler())
            .then(this.getReply());
    }

    Mono<Message> getReply();

    Mono<Void> interactionReply(InteractionApplicationCommandCallbackSpec interactionApplicationCommandCallbackSpec);

}
