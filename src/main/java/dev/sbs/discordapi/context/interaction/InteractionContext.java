package dev.sbs.discordapi.context.interaction;

import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

public interface InteractionContext<T extends InteractionCreateEvent> extends EventContext<T> {

    @Override
    default Mono<Message> buildMessage(@NotNull Response response) {
        return this.interactionEdit(response.getD4jComponentCallbackSpec())
            .publishOn(response.getReactorScheduler())
            .then(this.getReply());
    }

    Mono<Message> getReply();

    Mono<Void> interactionEdit(InteractionApplicationCommandCallbackSpec interactionApplicationCommandCallbackSpec);

}
