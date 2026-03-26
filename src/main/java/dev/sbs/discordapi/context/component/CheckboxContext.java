package dev.sbs.discordapi.context.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.interaction.Checkbox;
import dev.sbs.discordapi.handler.response.Followup;
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
 * Context for checkbox component interactions, extending {@link ActionComponentContext}
 * with access to the toggled {@link Checkbox} and a convenience method for modifying
 * it via its builder.
 *
 * <p>
 * Instances are created via the {@link #of} factory method when a
 * {@link ComponentInteractionEvent} targeting a checkbox is dispatched.
 */
public interface CheckboxContext extends ActionComponentContext {

    /** {@inheritDoc} */
    @Override
    @NotNull ComponentInteractionEvent getEvent();

    /** {@inheritDoc} */
    @Override
    @NotNull Checkbox getComponent();

    /**
     * Modifies the checkbox by applying a transformation function to its builder,
     * then replaces it in the current response page.
     *
     * @param checkboxBuilder a function that transforms the checkbox builder
     * @return a mono completing when the checkbox has been updated in the page
     */
    default Mono<Void> modify(@NotNull Function<Checkbox.Builder, Checkbox.Builder> checkboxBuilder) {
        return this.modify(checkboxBuilder.apply(this.getComponent().mutate()).build());
    }

    /**
     * Creates a new {@code CheckboxContext} for the given event, response, and checkbox.
     *
     * @param discordBot the bot instance
     * @param event the component interaction event
     * @param response the cached response containing the checkbox
     * @param checkbox the checkbox that was toggled
     * @param followup the associated followup, if any
     * @return a new checkbox context
     */
    static @NotNull CheckboxContext of(@NotNull DiscordBot discordBot, @NotNull ComponentInteractionEvent event, @NotNull Response response, @NotNull Checkbox checkbox, @NotNull Optional<Followup> followup) {
        return new Impl(
            discordBot,
            event,
            response.getUniqueId(),
            checkbox,
            followup
        );
    }

    /**
     * Default implementation of {@link CheckboxContext}.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements CheckboxContext {

        /** The bot instance. */
        private final @NotNull DiscordBot discordBot;

        /** The underlying component interaction event. */
        private final @NotNull ComponentInteractionEvent event;

        /** The unique response identifier linking this context to its cached response. */
        private final @NotNull UUID responseId;

        /** The checkbox that was toggled. */
        private final @NotNull Checkbox component;

        /** The associated followup, if any. */
        private final @NotNull Optional<Followup> followup;

    }

}
