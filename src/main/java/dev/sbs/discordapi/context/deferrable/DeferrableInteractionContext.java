package dev.sbs.discordapi.context.deferrable;

import dev.sbs.discordapi.context.InteractionContext;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.InteractionCallbackSpec;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

public interface DeferrableInteractionContext<T extends DeferrableInteractionEvent> extends InteractionContext<T> {

    @Override
    default Mono<Message> discordBuildMessage(@NotNull Response response) {
        return this.getEvent()
            .editReply(response.getD4jInteractionReplyEditSpec())
            .publishOn(response.getReactorScheduler());
    }

    default Mono<Message> discordEditMessage(@NotNull Response response) {
        return this.discordBuildMessage(response);
    }

    default Mono<Void> deferReply() {
        return this.deferReply(false);
    }

    default Mono<Void> deferReply(boolean ephemeral) {
        return this.getEvent().deferReply(InteractionCallbackSpec.builder().ephemeral(ephemeral).build());
    }

    default Mono<Message> getReply() {
        return this.getEvent().getReply();
    }

}
