package dev.sbs.discordapi.context.component;

import dev.sbs.discordapi.component.interaction.Modal;
import dev.sbs.discordapi.component.type.UserInteractable;
import dev.sbs.discordapi.context.DeferrableInteractionContext;
import dev.sbs.discordapi.context.message.MessageContext;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.InteractionCallbackSpec;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * Context for component interactions (buttons, select menus, modals), extending both
 * {@link MessageContext} and {@link DeferrableInteractionContext} with component-specific
 * capabilities such as deferred edits, followup management, and modal presentation.
 *
 * <p>
 * Component contexts operate on an existing message and its associated {@link CachedResponse},
 * providing overridden Discord API methods that use interaction-based followup and edit
 * endpoints rather than standard message endpoints.
 *
 * @see ActionComponentContext
 * @see ModalContext
 */
public interface ComponentContext extends MessageContext<ComponentInteractionEvent>, DeferrableInteractionContext<ComponentInteractionEvent> {

    /**
     * Defers the reply and then creates an interaction followup message for the given response.
     *
     * @param response the response to send as a followup
     * @return a mono emitting the created followup message
     */
    @Override
    default Mono<Message> discordBuildFollowup(@NotNull Response response) {
        return this.deferReply(response.isEphemeral()).then(
            this.getEvent()
                .createFollowup(response.getD4jInteractionFollowupCreateSpec())
                .publishOn(response.getReactorScheduler())
        );
    }

    /**
     * Defers the edit and then deletes the followup message matching the given identifier.
     *
     * @param identifier the followup identifier
     * @return a mono completing when the followup is deleted
     */
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

    /**
     * Defers the edit and then edits the followup message matching the given identifier
     * with the provided response.
     *
     * @param identifier the followup identifier
     * @param response the updated response content
     * @return a mono emitting the edited followup message
     */
    @Override
    default Mono<Message> discordEditFollowup(@NotNull String identifier, @NotNull Response response) {
        return this.deferEdit(response.isEphemeral()).then(
            Mono.justOrEmpty(this.getFollowup(identifier))
            .flatMap(followup -> this.getEvent().editFollowup(followup.getMessageId(), response.getD4jInteractionReplyEditSpec()))
            .publishOn(response.getReactorScheduler())
        );
    }

    /**
     * Edits the original interaction message with the given response. If the interaction
     * has already been deferred, edits via the deferred reply; otherwise uses a component
     * callback edit.
     *
     * @param response the updated response content
     * @return a mono emitting the edited message
     */
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

    /**
     * Defers the component interaction edit as a non-ephemeral acknowledgment.
     *
     * @return a mono completing when the deferral is acknowledged
     */
    default Mono<Void> deferEdit() {
        return this.deferEdit(false);
    }

    /**
     * Defers the component interaction edit, marking the cached response as deferred.
     *
     * @param ephemeral whether the deferred response should be ephemeral
     * @return a mono completing when the deferral is acknowledged
     */
    default Mono<Void> deferEdit(boolean ephemeral) {
        return this.getEvent()
            .deferEdit(InteractionCallbackSpec.builder().ephemeral(ephemeral).build())
            .then(Mono.fromRunnable(() -> this.getResponseCacheEntry().setDeferred()));
    }

    /** {@inheritDoc} */
    @Override
    default Mono<MessageChannel> getChannel() {
        return DeferrableInteractionContext.super.getChannel();
    }

    /** The interactive component that triggered this interaction. */
    @NotNull UserInteractable getComponent();

    /** {@inheritDoc} */
    @Override
    default Mono<Message> getMessage() {
        return Mono.justOrEmpty(this.getEvent().getMessage());
    }

    /** {@inheritDoc} */
    @Override
    default Snowflake getMessageId() {
        return this.getEvent().getMessageId();
    }

    /**
     * Presents a {@link Modal} dialog to the user and registers it in the cached response
     * for later submission handling.
     *
     * @param modal the modal to present
     * @return a mono completing when the modal is presented
     */
    default Mono<Void> presentModal(@NotNull Modal modal) {
        return Mono.justOrEmpty(this.getResponseCacheEntry())
            .doOnNext(entry -> entry.setUserModal(this.getInteractUser(), modal))
            .flatMap(entry -> this.getEvent().presentModal(modal.getD4jPresentSpec()));
    }

}