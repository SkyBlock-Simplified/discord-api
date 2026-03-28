package dev.sbs.discordapi.context.scope;

import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.InteractionCallbackSpec;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * Interaction scope for contexts wrapping deferrable Discord interactions, adding
 * the ability to defer, edit, and retrieve the interaction reply.
 *
 * <p>
 * Unlike the base {@link InteractionContext}, deferrable interactions build and edit
 * messages through the interaction reply mechanism
 * ({@link DeferrableInteractionEvent#editReply}) rather than sending new channel messages.
 *
 * <p>
 * This scope is the parent of both command and component context hierarchies:
 * <ul>
 *   <li><b>{@link CommandContext}</b> - slash, user, and message commands</li>
 *   <li><b>{@link ComponentContext}</b> - buttons, select menus, and modals</li>
 * </ul>
 *
 * @param <T> the Discord4J {@link DeferrableInteractionEvent} subtype wrapped by this context
 * @see InteractionContext
 */
public interface DeferrableInteractionContext<T extends DeferrableInteractionEvent> extends InteractionContext<T> {

    /**
     * {@inheritDoc}
     *
     * <p>
     * Overrides the default channel-based message creation to use the interaction reply
     * edit mechanism instead.
     */
    @Override
    default Mono<Message> discordBuildMessage(@NotNull Response response) {
        return this.getEvent()
            .editReply(response.getD4jInteractionReplyEditSpec())
            .publishOn(response.getReactorScheduler());
    }

    /**
     * Edits the interaction reply with the given {@link Response}. Delegates to
     * {@link #discordBuildMessage(Response)}.
     *
     * @param response the response to apply as an edit
     * @return a {@link Mono} emitting the edited message
     */
    default Mono<Message> discordEditMessage(@NotNull Response response) {
        return this.discordBuildMessage(response);
    }

    /**
     * Defers the interaction reply with a non-ephemeral acknowledgement, allowing the bot
     * to respond later via {@link #discordBuildMessage(Response)}.
     *
     * @return a {@link Mono} completing when the defer has been acknowledged
     */
    default Mono<Void> deferReply() {
        return this.deferReply(false);
    }

    /**
     * Defers the interaction reply, optionally as an ephemeral acknowledgement, allowing
     * the bot to respond later via {@link #discordBuildMessage(Response)}.
     *
     * @param ephemeral {@code true} to make the deferred reply visible only to the invoking user
     * @return a {@link Mono} completing when the defer has been acknowledged
     */
    default Mono<Void> deferReply(boolean ephemeral) {
        return this.getEvent().deferReply(InteractionCallbackSpec.builder().ephemeral(ephemeral).build());
    }

    /** The initial reply {@link Message} for this interaction. */
    default Mono<Message> getReply() {
        return this.getEvent().getReply();
    }

}
