package dev.sbs.discordapi.context.interaction.deferrable;

import dev.sbs.discordapi.context.interaction.InteractionContext;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.InteractionCallbackSpec;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;

public interface DeferrableInteractionContext<T extends DeferrableInteractionEvent> extends InteractionContext<T> {

    @Override
    default Mono<Message> buildMessage(@NotNull Response response) {
        return this.getEvent()
            .editReply(response.getD4jInteractionReplyEditSpec())
            .publishOn(response.getReactorScheduler());
    }

    @Override
    default Mono<Void> deferReply(boolean ephemeral, @NotNull Optional<String> content) {
        return this.getEvent()
            .deferReply(InteractionCallbackSpec.builder().ephemeral(ephemeral).build())
            .then(this.edit(content));
    }

}
