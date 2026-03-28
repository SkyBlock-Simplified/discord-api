package dev.sbs.discordapi.context.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.interaction.RadioGroup;
import dev.sbs.discordapi.context.scope.ActionComponentContext;
import dev.sbs.discordapi.handler.response.ResponseFollowup;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Context for radio group component interactions, extending {@link ActionComponentContext}
 * with access to the interacted {@link RadioGroup} and a convenience method for modifying
 * it via its builder.
 *
 * <p>
 * Instances are created via the {@link #of} factory method when a
 * {@link ComponentInteractionEvent} targeting a radio group is dispatched.
 */
public interface RadioGroupContext extends ActionComponentContext {

    /** {@inheritDoc} */
    @Override
    @NotNull ComponentInteractionEvent getEvent();

    /** {@inheritDoc} */
    @Override
    @NotNull RadioGroup getComponent();

    /**
     * Modifies the radio group by applying a transformation function to its builder,
     * then replaces it in the current response page.
     *
     * @param radioGroupBuilder a function that transforms the radio group builder
     * @return a mono completing when the radio group has been updated in the page
     */
    default Mono<Void> modify(@NotNull Function<RadioGroup.Builder, RadioGroup.Builder> radioGroupBuilder) {
        return this.modify(radioGroupBuilder.apply(this.getComponent().mutate()).build());
    }

    /**
     * Creates a new {@code RadioGroupContext} for the given event, response, and radio group.
     *
     * @param discordBot the bot instance
     * @param event the component interaction event
     * @param response the cached response containing the radio group
     * @param radioGroup the radio group that was interacted with
     * @param followup the associated followup, if any
     * @return a new radio group context
     */
    static @NotNull RadioGroupContext of(@NotNull DiscordBot discordBot, @NotNull ComponentInteractionEvent event, @NotNull Response response, @NotNull RadioGroup radioGroup, @NotNull Optional<ResponseFollowup> followup) {
        return new Impl(
            discordBot,
            event,
            response.getUniqueId(),
            radioGroup,
            followup
        );
    }

    /**
     * Default implementation of {@link RadioGroupContext}.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements RadioGroupContext {

        /** The bot instance. */
        private final @NotNull DiscordBot discordBot;

        /** The underlying component interaction event. */
        private final @NotNull ComponentInteractionEvent event;

        /** The unique response identifier linking this context to its cached response. */
        private final @NotNull UUID responseId;

        /** The radio group that was interacted with. */
        private final @NotNull RadioGroup component;

        /** The associated followup, if any. */
        private final @NotNull Optional<ResponseFollowup> followup;

    }

}
