package dev.sbs.discordapi.context.deferrable.component.action;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.handler.response.Followup;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.component.interaction.action.Button;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Context for button component interactions, extending {@link ActionComponentContext}
 * with access to the clicked {@link Button} and a convenience method for modifying
 * the button via its builder.
 *
 * <p>
 * Instances are created via the {@link #of} factory method when a
 * {@link ButtonInteractionEvent} is dispatched.
 */
public interface ButtonContext extends ActionComponentContext {

    /**
     * Returns the underlying {@link ButtonInteractionEvent}.
     *
     * @return the button interaction event
     */
    @Override
    @NotNull ButtonInteractionEvent getEvent();

    /**
     * Returns the {@link Button} that was clicked to trigger this interaction.
     *
     * @return the clicked button
     */
    @Override
    @NotNull Button getComponent();

    /**
     * Modifies the clicked button by applying a transformation function to its builder,
     * then replaces the button in the current response page.
     *
     * @param buttonBuilder a function that transforms the button builder
     * @return a mono completing when the button has been updated in the page
     */
    default Mono<Void> modify(@NotNull Function<Button.Builder, Button.Builder> buttonBuilder) {
        return this.modify(buttonBuilder.apply(this.getComponent().mutate()).build());
    }

    /**
     * Creates a new {@code ButtonContext} for the given event, response, and button.
     *
     * @param discordBot the bot instance
     * @param event the button interaction event
     * @param response the cached response containing the button
     * @param button the button that was clicked
     * @param followup the associated followup, if any
     * @return a new button context
     */
    static @NotNull ButtonContext of(@NotNull DiscordBot discordBot, @NotNull ButtonInteractionEvent event, @NotNull Response response, @NotNull Button button, @NotNull Optional<Followup> followup) {
        return new Impl(
            discordBot,
            event,
            response.getUniqueId(),
            button,
            followup
        );
    }

    /**
     * Default implementation of {@link ButtonContext}.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements ButtonContext {

        /** The bot instance. */
        private final @NotNull DiscordBot discordBot;

        /** The underlying button interaction event. */
        private final @NotNull ButtonInteractionEvent event;

        /** The unique response identifier linking this context to its cached response. */
        private final @NotNull UUID responseId;

        /** The button that was clicked. */
        private final @NotNull Button component;

        /** The associated followup, if any. */
        private final @NotNull Optional<Followup> followup;

    }

}
